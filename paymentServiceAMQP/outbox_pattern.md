# Transactional Outbox Pattern in paymentServiceAMQP

To avoid dual-write inconsistencies (where a database write succeeds but the message broker is down, or vice versa), the [paymentServiceAMQP](.) module implements the **Transactional Outbox pattern**.

---

## 📬 Pattern Flow

1.  **Atomicity**: In [PaymentService.java](src/main/java/com/saha/amit/orderService/paymentService/service/PaymentService.java#L64-L93), both the `Payment` record and the `OutboxEvent` are written to the database in a single R2DBC transaction. If either fails, the entire transaction rolls back.
2.  **At-Least-Once Delivery**: The [OutboxPublisher.java](src/main/java/com/saha/amit/orderService/paymentService/service/OutboxPublisher.java) scheduler polls the `outbox_payment` table every 2 seconds for records with `published = false`.
3.  **Broker Acknowledgment**: The publisher sends the message using RabbitMQ's **Publisher Confirms**. The outbox event is only marked as `published = true` after the RabbitMQ broker responds with a successful ACK.
4.  **Idempotence**: Consumers handle potential duplicate messages by checking if the resource has already reached the desired state (e.g., in [RabbitMessageListener.java](src/main/java/com/saha/amit/orderService/paymentService/messaging/RabbitMessageListener.java#L74-L106), checking if payment status is already `IN_PROGRESS` or `COMPLETED`).

---

## 📊 Database Schema

The database relies on two primary tables within the `order_schema` schema:

```sql
CREATE TABLE `payments` (
  `payment_id` VARCHAR(36) NOT NULL,
  `order_id` VARCHAR(36) NOT NULL,
  `payment_status` VARCHAR(20) NOT NULL,
  `amount` DECIMAL(10,2) NOT NULL,
  `payment_type` VARCHAR(20) NOT NULL,
  `card_no` VARCHAR(25) DEFAULT NULL,
  `account_no` VARCHAR(30) DEFAULT NULL,
  `upi_id` VARCHAR(50) DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`payment_id`)
) ENGINE=InnoDB;

CREATE TABLE outbox_payment (
    payment_id CHAR(36) PRIMARY KEY,
    aggregate_id CHAR(36), -- paymentId / orderId
    aggregate_type VARCHAR(50), -- "Payment"
    event_type VARCHAR(50),     -- "payment.created"
    payload JSON,
    created_at TIMESTAMP,
    published BOOLEAN DEFAULT FALSE
);
```

---

## 🔄 Interaction Diagram

```
  [Database Transaction]
  +--------------------------------+
  | 1. Save Payment (IN_PROGRESS)  |
  | 2. Save Outbox Event (Pending) |
  +---------------+----------------+
                  | (committed)
                  v
       [outbox_payment Table]
                  |
                  | (Poll every 2s)
                  v
          [OutboxPublisher] -------(Publish)-------> [RabbitMQ Broker]
                  ^                                         |
                  |-----------(Publisher Confirm ACK)-------|
                  | (Update status to published=true)
                  v
       [outbox_payment Table]
```
