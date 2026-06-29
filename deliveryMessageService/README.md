# deliveryMessageService

This microservice acts as the delivery router. It consumes payment success notifications and forwards them to simulate order shipping in a non-blocking, reactive manner using **Spring WebFlux** and **reactor-rabbitmq**.

---

## ⚙️ Functionality & Flow

1.  **Payment Success Event Consumption**: Listens to the `payment.success` event on the `delivery-service-queue` using manual ACKs.
2.  **Publish Event**: Simulates dispatch by forwarding the payload as `delivery.created` to the `domain.events` exchange.
3.  **Durable Database Log**: Exposes repositories to record delivery statuses inside the MySQL `delivery` table.

---

## 🛠️ Code Structure & Key Classes

*   [DeliveryQueueListener.java](src/main/java/com/saha/amit/messaging/DeliveryQueueListener.java): Subscribes to the target queue using reactor-rabbitmq's `Receiver#consumeManualAck`. Dispatches to the publisher, and ACKs the message only upon successful delivery confirmation.
*   [DeliveryEventPublisher.java](src/main/java/com/saha/amit/messaging/DeliveryEventPublisher.java): Directs the event payload to RabbitMQ using reactor-rabbitmq's `Sender#sendWithPublishConfirms`.
*   [CustomDeliveryRepositoryImpl.java](src/main/java/com/saha/amit/repository/CustomDeliveryRepositoryImpl.java): Provides custom database operations for R2DBC inserts of `Delivery` records.

---

## 📊 Database & Messaging Contracts

### Database Table: `delivery`
```sql
CREATE TABLE `delivery` (
  `delivery_id` VARCHAR(36) NOT NULL,
  `order_id` VARCHAR(36) NOT NULL,
  `delivery_status` VARCHAR(20) NOT NULL,
  `address_line1` VARCHAR(100) NOT NULL,
  `address_line2` VARCHAR(100) DEFAULT NULL,
  `city` VARCHAR(50) NOT NULL,
  `state` VARCHAR(50) NOT NULL,
  `postal_code` VARCHAR(15) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`delivery_id`)
) ENGINE=InnoDB;
```

### Messaging Bindings
*   **Publishes**: `delivery.created` routing key to `domain.events` exchange.
*   **Consumes**: Binds `delivery-service-queue` to `domain.events` exchange for the routing key `payment.success`.

---

## 🧪 Verification & Testing Steps

To verify the delivery service:

### 1. View Service Startup and Listening Logs
Ensure the service is running and listening on the RabbitMQ queue. You should see logs indicating subscription:
```
INFO: Subscribing to queue: delivery-service-queue
```

### 2. Trace Event Processing Logs
When an order is successfully paid, trace the delivery logs:
```
📥 Received from delivery-service-queue: {"orderId":"...", "customerId":"...", "payment":{...}}
...
✅ Broker confirmed publish to exchange=domain.events, routingKey=delivery.created
✅ Message acked: ...
```
*   **Expected Behavior**: The service intercepts the `payment.success` event, publishes `delivery.created` with a successful broker confirm ACK, and then acknowledges the incoming message on RabbitMQ.

### 3. Verify Database Records
Query the MySQL database to check delivery logs:
```sql
USE order_schema;
SELECT * FROM delivery;
```
*   **Expected Output**: Columns should correctly map the order address and show the shipping state as `IN_PROGRESS` or `COMPLETED`.
