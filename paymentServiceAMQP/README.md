# paymentServiceAMQP

This microservice processes payments for placed orders. It implements traditional **Spring AMQP** with **manual acknowledgments**, R2DBC transactions, and the **Transactional Outbox pattern** to prevent dual-write anomalies.

---

## ⚙️ Functionality & Flow

1.  **Order Created Event Consumption**: Listens to the `order.created` event on the `payment-service-queue`.
2.  **Transactional Outbox Save**: Inserts a new `Payment` record (`IN_PROGRESS`) and a corresponding event in the `outbox_payment` table inside a single local transaction.
3.  **Outbox Poller Relay**: The `OutboxPublisher` polls the outbox table every 2 seconds, publishes the events as `payment.created` to RabbitMQ, and updates `published = true` only upon receiving a Publisher Confirm ACK from the broker.
4.  **Self-Consumption (Payment Processing)**: Consumes the `payment.created` event, simulates card charging/validation, saves the final payment status (`COMPLETED` or `FAILED`), and broadcasts `payment.success` or `payment.failure` events.
5.  **Refund Handling**: Subscribes to downstream `delivery.failure` events to issue refunds, updating status to `REFUND`.

---

## 🛠️ Code Structure & Key Classes

*   [RabbitMessageListener.java](src/main/java/com/saha/amit/orderService/paymentService/messaging/RabbitMessageListener.java): Implements the AMQP listeners. Manages manual ACKs (`basicAck`/`basicNack`) to guarantee no message is discarded prematurely.
*   [PaymentService.java](src/main/java/com/saha/amit/orderService/paymentService/service/PaymentService.java): Runs R2DBC database inserts for payments and outbox records in a single transaction block.
*   [OutboxPublisher.java](src/main/java/com/saha/amit/orderService/paymentService/service/OutboxPublisher.java): Periodically polls for unsent events and relays them to RabbitMQ.
*   [PaymentPublisher.java](src/main/java/com/saha/amit/orderService/paymentService/messaging/PaymentPublisher.java): Interacts with RabbitTemplate and registers listener callbacks to handle publisher confirms.

👉 For detailed conceptual diagrams, database tables, and rollback workflows, see the **[Transactional Outbox Pattern Documentation](outbox_pattern.md)**.

---

## 📊 Database & Messaging Contracts

### Database Tables: `payments` & `outbox_payment`
The service reads and writes to two primary MySQL tables under the `order_schema` schema. Refer to the schema definitions inside [outbox_pattern.md](outbox_pattern.md#L16-L43).

### Messaging Bindings
*   **Publishes**: 
    *   `payment.created` (via Outbox relay)
    *   `payment.success` (upon successful authorization)
    *   `payment.failure` (upon payment declined)
*   **Consumes**: Binds `payment-service-queue` to `domain.events` for:
    *   `order.created`
    *   `payment.created` (for self-processing)
    *   `delivery.failure` (triggers refund logic)

---

## 🧪 Verification & Testing Steps

To verify the payment flow and outbox behavior:

### 1. Place an Order & Check Outbox Table
Initiate order placement, which will write an outbox record:
```bash
# Place order (e.g. customer_id = cust-202)
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{"customerId":"cust-202","customerName":"Jane","payment":{"amount":45.00,"paymentType":"CARD","cardNo":"1111-2222-3333-4444"}}'
```
Immediately query the outbox table to see the stored event:
```sql
USE order_schema;
SELECT event_type, payload, published FROM outbox_payment;
```
*   **Expected Output**: The outbox event with `event_type = 'payment.created'` should have its `published` column marked as `1` (true), indicating the poller successfully published it and received the broker confirm.

### 2. Verify Payment Completion
Check the payments status after processing completes:
```sql
SELECT payment_id, order_id, payment_status, amount FROM payments;
```
*   **Expected Output**: The payment record status should be `COMPLETED`.
