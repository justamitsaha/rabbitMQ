# paymentServiceAMQP Detailed Flow Guide

This document provides a detailed breakdown of the execution flows, R2DBC transactions, publisher confirms, and outbox polling patterns implemented inside the **paymentServiceAMQP** module.

---

## 🏛️ Component Architecture Map

The payment service combines R2DBC (Reactive SQL) and traditional Spring AMQP with manual acknowledgments to handle payment lifecycle and transaction safety:

```
[RabbitMQ Broker]
    |
    | (Listen payment-service-queue)
    v
[RabbitMessageListener]
    |
    +---> [PaymentService] ---> (R2DBC) ---> [MySQL Database]
    |                                            | (payments & outbox_payment tables)
    v                                            v
[PaymentPublisher] <------------------- [OutboxPublisher] (Scheduled Poller)
```

---

## 🔄 Detailed Operational Flows

### Part 1: Order Created Ingestion & Outbox Save
When an order is created, the payment service logs it alongside a pending outbox event inside a single database transaction.

```
RabbitMQ           RabbitMessageListener         PaymentService          MySQL (ACID Transaction)
   |                         |                         |                            |
   |-- order.created ------->|                         |                            |
   |                         |-- processPayment() ---->|                            |
   |                         |   (status is null)      |-- processInitialOrder() -->|
   |                         |                         |                            |-- [1] Save Payment (IN_PROGRESS)
   |                         |                         |                            |-- [2] Save OutboxEvent (published=0)
   |                         |                         |<-- commit transaction -----|
   |                         |<-- void ----------------|                            |
   |<-- manual ACK ----------|                         |                            |
```

1.  **Consume `order.created`**: The [RabbitMessageListener.java](src/main/java/com/saha/amit/orderService/paymentService/messaging/RabbitMessageListener.java) consumes the event.
2.  **Verify State**: It detects that `paymentStatus` is null (indicating a new order).
3.  **ACID Transaction**: It initiates `paymentService.processInitialOrder()` which executes both DB inserts atomically:
    *   Inserts a record in the `payments` table with status `IN_PROGRESS`.
    *   Inserts a record in the `outbox_payment` table containing the payload for the `payment.created` event, with `published = false`.
4.  **Acknowledge Broker**: Only after the database transaction successfully commits, the listener invokes `channel.basicAck()` to acknowledge the message. If the DB write fails, it calls `basicNack()` to route the message to the DLQ.

---

### Part 2: Outbox Poller & Broker Publish Confirm
A background poller periodically queries the database and publishes pending events to the broker, marking them completed only after the broker acknowledges receipt.

```
OutboxPublisher                  MySQL (Database)                      RabbitMQ Event Bus
       |                                |                                       |
       | (runs every 2s)                |                                       |
       |-- Query unpublished ---------->|                                       |
       |<-- List of OutboxEvents -------|                                       |
       |                                                                        |
       |-- publishEvent() ----------------------------------------------------->| (payment.created)
       |<-- Confirm ACK (Broker Confirm) ---------------------------------------|
       |                                                                        |
       |-- Mark published=1 ----------->|                                       |
```

1.  **Scheduled Polling**: [OutboxPublisher.java](src/main/java/com/saha/amit/orderService/paymentService/service/OutboxPublisher.java) executes every 2 seconds (`@Scheduled(fixedDelay = 2000)`).
2.  **Database Query**: It fetches a list of `OutboxEvent` records where `published = false`.
3.  **Publish with Confirm**: For each event, it publishes the payload as `payment.created` to RabbitMQ via [PaymentPublisher.java](src/main/java/com/saha/amit/orderService/paymentService/messaging/PaymentPublisher.java).
4.  **Confirm Handling**: The publisher blocks waiting for the broker's confirm ACK. Once received, it writes `published = true` back to the database.

---

### Part 3: Card Authorization & Event Broadcast (Self-Processing)
Once the `payment.created` event is published, the service consumes it to execute the actual payment charging simulation.

```
RabbitMQ           RabbitMessageListener         PaymentService         PaymentPublisher          MySQL DB
   |                         |                         |                        |                     |
   |-- payment.created ----->|                         |                        |                     |
   |                         |-- processPayment() ---->|                         |                     |
   |                         |   (status=IN_PROGRESS)  |-- savePayment() ------>|                     |
   |                         |                         |                        |-- save (COMPLETED)->|
   |                         |                         |<-- saved payment ------|                     |
   |                         |                         |                                              |
   |                         |                         |-- publishEvent(payment.success) ------------>|
   |                         |                         |<-- ACK confirm ------------------------------|
   |                         |<-- void ----------------|                                              |
   |<-- manual ACK ----------|                         |                                              |
```

1.  **Consume `payment.created`**: The listener receives the event it just published (Self-Processing).
2.  **Verify State**: It detects `paymentStatus = IN_PROGRESS`.
3.  **Process Charge**: The service updates the status to `COMPLETED` (or `FAILED` if authorization fails) and persists it.
4.  **Publish Result**: It publishes `payment.success` (or `payment.failure`) directly to the `domain.events` exchange.
5.  **Acknowledge Broker**: The listener manual ACKs the `payment.created` event.

---

### Part 4: Downstream Refund Compensation
*   **Trigger**: If delivery fails downstream, `deliveryMessageService` emits `delivery.failure`.
*   **Flow**: The listener detects `delivery.failure`, calls refund logic in the service (which updates the database state to `REFUND`), and then issues a manual ACK.

---

## 🛡️ Edge Cases & Reliability Guarantees

*   **Broker Outages**: If RabbitMQ is down when the order is created, the outbox write still succeeds. The poller keeps retrying to publish the pending outbox events every 2 seconds until the broker recovers.
*   **Duplicate Deliveries**: The listener checks the database state before initiating payments. If the payment record is already `COMPLETED` or `IN_PROGRESS`, it ignores duplicate `order.created` messages, enforcing consumer idempotence.
