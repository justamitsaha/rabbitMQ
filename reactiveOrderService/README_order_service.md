# reactiveOrderService Detailed Flow Guide

This document provides a detailed breakdown of the execution flows, component interactions, and failure scenarios of the **reactiveOrderService**.

---

## 🚀 Architectural Component Map

The service utilizes Spring WebFlux and reactor-rabbitmq to orchestrate reactive, non-blocking operations:

```
[Client]
   |
   | (HTTP POST /orders)
   v
[OrderController.java]
   |
   | (placeOrder)
   v
[OrderService.java]
   +---> (R2DBC CustomOrderRepository.java) ---> [MySQL orders Table]
   |
   +---> (publishEvent)
            |
            v
     [OrderPublisher.java]
            |
            | (sendWithPublishConfirms)
            v
     [RabbitMQ Broker] <---(Listen order-service-queue)--- [RabbitListeners.java]
                                                                  |
                                                           (updateOrderStatus)
                                                                  v
                                                           [OrderService]
```

---

## 🔄 Detailed Operational Flows

### Flow A: Order Placement Ingestion (Producer Flow)

This flow is initiated by a client attempting to place a new order. It enforces **DB-First semantics** paired with **Publisher Confirms** to guarantee that no order is accepted unless its creation event is safely enqueued.

```
Client         OrderController       OrderService      CustomOrderRepository       OrderPublisher         RabbitMQ
  |                  |                    |                      |                       |                 |
  |-- POST /orders ->|                    |                      |                       |                 |
  |                  |--- placeOrder() -->|                      |                       |                 |
  |                  |                    |--- insert(Order) --->|                       |                 |
  |                  |                    |<-- savedOrder -------|                       |                 |
  |                  |                    |                                              |                 |
  |                  |                    |----------------- publishEvent() ------------>|                 |
  |                  |                    |                                              |-- send msg ---->|
  |                  |                    |                                              |<-- ACK ---------|
  |                  |                    |<---------------- event ACK ------------------|                 |
  |<-- HTTP 200 -----|<-- savedOrder -----|                                              |                 |
```

1.  **Request Ingestion**: The client sends a `POST /orders` request to the REST endpoint in [OrderController.java](src/main/java/com/saha/amit/orderService/controller/OrderController.java).
2.  **Entity Initialization**: In [OrderService.java](src/main/java/com/saha/amit/orderService/service/OrderService.java), the system:
    *   Generates a unique `orderId` (UUID).
    *   Configures a logging context `correlationId` using MDC.
    *   Builds an `Order` aggregate with status `IN_PROGRESS`.
3.  **Local Database Persistence**: The service inserts the new order record into the MySQL database using [CustomOrderRepository.java](src/main/java/com/saha/amit/orderService/repository/CustomOrderRepository.java).
4.  **Broker Broadcast**: Once the database write succeeds, [OrderPublisher.java](src/main/java/com/saha/amit/orderService/messaging/OrderPublisher.java) is called:
    *   It serializes the payload (order info + payment and delivery details).
    *   It creates a RabbitMQ message binding a correlation header: `X-Correlation-ID = orderId`.
    *   It sends the message to the `domain.events` exchange with routing key `order.created`.
5.  **Publisher Confirm Acknowledgment**:
    *   The publisher uses reactor-rabbitmq's `sendWithPublishConfirms(Mono.just(msg))`. This reactive pipeline suspends execution until the broker returns an ACK or NACK confirm.
    *   **ACK Received (Success)**: The broker confirms the message is stored in queue. The flow completes and the client receives a `200 OK` response with the order in `IN_PROGRESS` state.
    *   **NACK/Exception Received (Failure)**: If the broker rejects the message or the connection fails:
        1.  The error is captured in `doOnError` inside the `placeOrder` pipeline.
        2.  A compensating query is executed to update the order status in the database to `FAILED`.
        3.  The client receives an `Internal Server Error` (HTTP 500), preventing dirty writes.

---

### Flow B: Saga Event Consumer (Listener Flow)

This flow runs in the background. It listens to downstream service responses on the event bus to complete or roll back (compensate) the active Saga transaction.

1.  **Background Subscription**: On startup, [RabbitListeners.java](src/main/java/com/saha/amit/orderService/listener/RabbitListeners.java) establishes a reactive consumer on the `order-service-queue` using `receiver.consumeAutoAck()`.
2.  **Message Received**: When a message arrives, the listener deserializes it and extracts the `orderId`.
3.  **Dynamic Routing & State Mutation**: The listener inspects the message's `routingKey` or delivery `type` headers:
    *   **Delivery Completed (`delivery.completed` / `delivery.success`)**:
        *   *Meaning*: Downstream payment and shipping succeeded.
        *   *Action*: Calls `orderService.updateOrderStatus(orderId, Status.COMPLETED)` to mark the order final.
    *   **Payment Failed (`payment.failed` / `payment.failure`)**:
        *   *Meaning*: Card was declined, bank transaction failed, or user requested Cash on Delivery (COD).
        *   *Action*: Triggers compensation. Calls `orderService.updateOrderStatus(orderId, Status.FAILED)`.
    *   **Delivery Failed (`delivery.failed` / `delivery.failure`)**:
        *   *Meaning*: Package couldn't be dispatched, inventory was insufficient, or delivery zipcode was 75034.
        *   *Action*: Triggers compensation. Calls `orderService.updateOrderStatus(orderId, Status.FAILED)`.

---

### Flow C: Real-Time Event Bus Tracking (Notification SSE Flow)

Exposes a streaming Server-Sent Events (SSE) feed of all actions occurring on the RabbitMQ event bus:

1.  **Background Wildcard listener**: On startup, [NotificationController.java](src/main/java/com/saha/amit/orderService/controller/NotificationController.java) consumes the `notification-service-queue` bound to the `#` wildcard.
2.  **Multicast Broadcast**: It extracts the event type and JSON payload and broadcasts it immediately to a shared, active multicast Sink.
3.  **SSE Streaming**: Clients invoking `GET /notifications` receive a real-time, non-blocking SSE stream (mime-type: `text/event-stream`) directly from this Sink.

---

## 🛡️ Edge Cases & Failure Semantics

### 1. Broker goes down before publishing
*   *Detection*: The reactive confirm stream returns an empty element or errors out.
*   *Mitigation*: The service catches this error in the reactive pipeline, rolls back the local database state to `FAILED`, logs the incident with the correlation ID, and returns a HTTP 500 error.

### 2. Duplicate Event Delivery (At-Least-Once Delivery)
*   *Issue*: RabbitMQ might redeliver `delivery.success` or `payment.failure` events.
*   *Mitigation*: The `updateOrderStatus` method is idempotent. It searches for the order, mutates the state, and saves it. If the order is already in a terminal state (`COMPLETED` or `FAILED`), it logs a warning and exits safely.

---

## 🧪 Verification & Testing Steps

### 1. Stream Real-Time Event Notifications
Start listening to the Server-Sent Events (SSE) notification stream:
```bash
curl -N http://localhost:8080/notifications
```

### 2. Trigger a Saga Workflow
In another terminal, place a successful order:
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-303","customerName":"Jane Doe","payment":{"amount":120.00,"paymentType":"CARD","cardNo":"1234-1234-1234-1234"},"delivery":{"addressLine1":"456 Pine St","city":"Boston","state":"MA","postalCode":"02111"}}'
```

### 3. Verify Log Events
Watch the stream terminal. You should see all intermediate events logged in real time:
```json
data:{"event":"order.created", "payload":{...}}
data:{"event":"payment.created", "payload":{...}}
data:{"event":"payment.success", "payload":{...}}
data:{"event":"delivery.created", "payload":{...}}
data:{"event":"delivery.success", "payload":{...}}
```

### 4. Query Orchestration Details
Verify the REST Orchestrator combines state from all three microservices. Run a GET query with the `orderId` generated in Step 2:
```bash
curl http://localhost:8080/orders/{orderId}/details
```
It returns the structured combined JSON response containing the Order database row, the Payment database row (from `paymentServiceAMQP`), and the Delivery database row (from `deliveryMessageService`):
```json
{
    "order": {
        "orderId": "...",
        "customerId": "cust-303",
        "orderStatus": "COMPLETED",
        ...
    },
    "payment": {
        "paymentId": "...",
        "paymentStatus": "COMPLETED",
        "amount": 120.0,
        ...
    },
    "delivery": {
        "deliveryId": "...",
        "deliveryStatus": "SUCCESS",
        "postalCode": "02111",
        ...
    }
}
```
