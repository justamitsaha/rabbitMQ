# reactiveOrderService

This microservice acts as the entry point of the e-commerce system. It handles order ingestion reactive-ly using **Spring WebFlux** and **reactor-rabbitmq**.

---

## ⚙️ Functionality & Flow

1.  **Order Ingestion**: Receives orders via the REST controller and saves them to the MySQL `orders` table with status `IN_PROGRESS`.
2.  **Publish Event**: Emits the `order.created` event to RabbitMQ's `domain.events` exchange.
3.  **Saga Lifecycle Listener**: Subscribes to downstream Saga events via RabbitMQ (`order-service-queue`) and completes or compensates (rolls back) the order state:
    *   `delivery.success` → Completes the order (`COMPLETED`).
    *   `delivery.failure` → Fails the order (`FAILED`).
    *   `payment.failure` → Fails the order (`FAILED`).

---

## 🛠️ Code Structure & Key Classes

*   [OrderController.java](src/main/java/com/saha/amit/orderService/controller/OrderController.java): Exposes reactive REST endpoints:
    *   `POST /orders`: Place a new order.
    *   `GET /orders`: Stream all orders using Server-Sent Events (SSE).
*   [OrderService.java](src/main/java/com/saha/amit/orderService/service/OrderService.java): Handles business logic, database operations via R2DBC, and coordinates event publication.
*   [OrderPublisher.java](src/main/java/com/saha/amit/orderService/messaging/OrderPublisher.java): Publishes messages to RabbitMQ with **Publisher Confirms** to guarantee delivery before resolving requests.
*   [RabbitListeners.java](src/main/java/com/saha/amit/orderService/listener/RabbitListeners.java): Handles incoming events (`delivery.success`, `delivery.failure`, `payment.failure`) to update order states.

---

## 📊 Database & Messaging Contracts

### Database Table: `orders`
```sql
CREATE TABLE `orders` (
  `order_id` VARCHAR(36) NOT NULL,
  `customer_id` VARCHAR(36) NOT NULL,
  `customer_name` VARCHAR(50) NOT NULL,
  `order_status` VARCHAR(20) NOT NULL,
  `amount` DECIMAL(10,2) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB;
```

### Messaging Bindings
*   **Publishes**: `order.created` routing key to `domain.events` exchange.
*   **Consumes**: Binds `order-service-queue` to `domain.events` for:
    *   `payment.failure`
    *   `delivery.success`
    *   `delivery.failure`

---

## 🧪 Verification & Testing Steps

To verify the functionality of the `reactiveOrderService`:

### 1. Place a Successful Order
Submit a POST request to place an order:
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-101",
    "customerName": "John Doe",
    "payment": {
      "amount": 150.00,
      "paymentType": "CARD",
      "cardNo": "1234-5678-9012-3456"
    },
    "delivery": {
      "addressLine1": "123 Main St",
      "city": "Boston",
      "state": "MA",
      "postalCode": "02108"
    }
  }'
```
*   **Expected Output**: HTTP 200 containing order details with status `IN_PROGRESS` and generated `orderId`.

### 2. Stream & Track Orders
Stream the orders status via SSE:
```bash
curl -N http://localhost:8080/orders
```
*   **Expected Output**: A stream of JSON objects. Watch the order transition from `IN_PROGRESS` to `COMPLETED` (if payment and delivery succeed) or `FAILED` (if any step fails).

### 3. Verify Database State
Query the MySQL container to check status:
```sql
USE order_schema;
SELECT order_id, order_status, amount FROM orders WHERE customer_id = 'cust-101';
```
