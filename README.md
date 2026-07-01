# Event-Driven Microservices with Spring Boot & RabbitMQ

This project is a multi-module Spring Boot application demonstrating event-driven microservices architecture using RabbitMQ as the message broker, R2DBC for reactive database access with MySQL, and the Transactional Outbox pattern.

---

## 📁 Repository Structure

*   [reactiveOrderService/](reactiveOrderService): Reactive order ingestion service using reactor-rabbitmq.
*   [paymentServiceAMQP/](paymentServiceAMQP): Payment processing service utilizing traditional Spring AMQP with manual ACKs, R2DBC transactions, and the Transactional Outbox pattern.
*   [deliveryMessageService/](deliveryMessageService): Lightweight reactive queue listener and publisher acting as a delivery status router.
*   [doc/](doc): Centralized infrastructure configuration files (Docker Compose, RabbitMQ definitions, MySQL init scripts) and architectural guides.
    *   👉 **[RabbitMQ Messaging Concepts Guide](doc/rabbitmq_concepts.md)**: Details queues, exchanges, bindings, routing keys, confirms, and retries.
    *   👉 **[Saga Pattern & Distributed Transactions](doc/saga_pattern.md)**: Compares choreography vs orchestration and maps out our system's Saga flow.
    *   👉 **[The Dual-Write Problem & Solutions](doc/dual_write_solutions.md)**: Details consistency models (ACID vs BASE) and solutions (Outbox, CDC, Saga, 2PC).
    *   👉 **[Transactional Outbox Pattern Comparisons](doc/outbox_pattern_comparison.md)**: Compares polling relays vs CDC, and DBA updates latency patterns.
    *   👉 **[Domain-Driven Design (DDD) Basics](doc/domain_driven_design.md)**: Explains tactical DDD patterns (Entities, Value Objects, Aggregates) and Bounded Contexts.
    *   👉 **[Event Payload Design Patterns](doc/event_payload_patterns.md)**: Analyzes Event-Carried State Transfer vs Event Notification vs Claim Check patterns, with polymorphic security recommendations.

---


## 🧱 Conceptual Architecture & Design

### System Topology & Event Flow

The system consists of three Spring Boot microservices interacting asynchronously via RabbitMQ. They handle order placement, payment processing, and delivery status routing using MySQL as the persistent store.

### 🔄 Saga Choreography Flow

The distributed transaction spanning these microservices is executed using a Choreography Saga pattern. Here is the complete end-to-end flow diagram:

```
Client      reactiveOrderService        RabbitMQ Event Bus       paymentServiceAMQP      deliveryMessageService
  |                  |                          |                        |                         |
  |-- POST /orders ->|                          |                        |                         |
  |                  |-- [1] Save Order         |                        |                         |
  |                  |   (IN_PROGRESS)          |                        |                         |
  |                  |                          |                        |                         |
  |                  |-- [2] Publish event ---->|                        |                         |
  |                  |   (order.created)        |                        |                         |
  |<-- HTTP 200 -----|                          |                        |                         |
  |                  |                          |-- [3] Deliver event -->|                         |
  |                  |                          |   (order.created)      |                         |
  |                  |                          |                        |-- [4] Save Payment      |
  |                  |                          |                        |   & OutboxEvent         |
  |                  |                          |                        |   (Single DB Tx)        |
  |                  |                          |                        |                         |
  |                  |                          |                        |-- [5] Outbox Poller     |
  |                  |                          |                        |   queries (every 2s)    |
  |                  |                          |                        |                         |
  |                  |                          |<-- [6] Publish event --|                         |
  |                  |                          |    (payment.created)   |                         |
  |                  |                          |--- [7] Confirm ACK --->|                         |
  |                  |                          |                        |-- [8] Mark Outbox       |
  |                  |                          |                        |   published = true      |
  |                  |                          |                        |                         |
  |                  |                          |-- [9] Deliver event -->|                         |
  |                  |                          |   (payment.created)    |                         |
  |                  |                          |                        |-- [10] Authorize card   |
  |                  |                          |                        |   & status=COMPLETED    |
  |                  |                          |                        |                         |
  |                  |                          |<-- [11] Publish success|                         |
  |                  |                          |    (payment.success)   |                         |
  |                  |                          |                        |                         |
  |                  |                          |----------------- [12] Deliver success --------->|
  |                  |                          |                  (payment.success)               |
  |                  |                          |                                                  |-- [13] Forward as
  |                  |                          |                                                  |   delivery.created
  |                  |                          |<---------------- [14] Publish event -------------|
  |                  |                          |                  (delivery.created)              |
  |                  |                          |                                                  |
  |                  |                          |----------------- [15] Deliver event ------------>|
  |                  |                          |                  (delivery.created)              |
  |                  |                          |                                                  |-- [16] Dispatch
  |                  |                          |                                                  |   package
  |                  |                          |<---------------- [17] Publish success -----------|
  |                  |                          |                  (delivery.success)              |
  |                  |                          |                                                  |
  |                  |<-- [18] Deliver success -|                                                  |
  |                  |    (delivery.success)    |                                                  |
  |                  |-- [19] Update status     |                                                  |
  |                  |   (COMPLETED)            |                                                  |
```

*   **Compensating Transactions (Rollback Paths)**:
    *   If payment fails (e.g. payment type is `CASH_ON_DELIVERY`), `paymentServiceAMQP` updates status to `FAILED` and publishes `payment.failure`. The `reactiveOrderService` consumes it and cancels the order (status: `FAILED`).
    *   If delivery fails (e.g. delivery zipcode is `75034`), `deliveryMessageService` publishes `delivery.failure`. The `reactiveOrderService` updates status to `FAILED`, and the `paymentServiceAMQP` compensates and reverts the payment status to `FAILED`.

### 📋 Saga Event Sequence Matrix

The table below outlines the sequential lifecycle of a successful transaction across the microservices, detailing the messaging properties and local actions at each stage:

| Step | Service / Actor | Event / Routing Key | Exchange / Queue | Action Performed | Result / Outcome                                                                                                                   |
| :--- | :--- | :--- | :--- | :--- |:-----------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Client** | *None (HTTP POST)* | `POST /orders` | Client submits order, payment, and shipping details. | Request received by Order Service.                                                                                                 |
| **2** | **reactiveOrderService** | `order.created` | Exchange: `domain.events`<br>Queue: `payment-service-queue` | Saves local Order record as `IN_PROGRESS` and publishes created event. | For key order.created Rabbit MQ routs request to exchange payment-service-queue<br/>Where it Awaits downstream payment initiation. |
| **3** | **paymentServiceAMQP** | `order.created` | Queue: `payment-service-queue` | Consumes event, saves local Payment (`IN_PROGRESS`) and Outbox event. | Transaction commits; manual ACK sent.                                                                                              |
| **4** | **paymentServiceAMQP** (Poller) | `payment.created` | Exchange: `domain.events`<br>Queue: `payment-service-queue` | Outbox Poller queries database (every 2s) and broadcasts the event. | Waits for broker ACK; marks outbox published.                                                                                      |
| **5** | **paymentServiceAMQP** | `payment.created` | Queue: `payment-service-queue` | Consumes `payment.created` (self-process), updates Payment to `COMPLETED`. | Local payment finalized; prepares success event.                                                                                   |
| **6** | **paymentServiceAMQP** | `payment.success` | Exchange: `domain.events`<br>Queue: `delivery-service-queue` | Publishes payment success notification; manual ACK sent for step 5 event. | Triggers shipping allocation downstream.                                                                                           |
| **7** | **deliveryMessageService** | `payment.success` | Queue: `delivery-service-queue` | Consumes payment success; delegates dispatch forwarding. | Initiates reactive forwarding; manual ACK sent.                                                                                    |
| **8** | **deliveryMessageService** | `delivery.created` | Exchange: `domain.events`<br>Queue: `delivery-service-queue` | Forwards event to exchange to signal delivery creation. | Awaits delivery shipment completion.                                                                                               |
| **9** | **deliveryMessageService** | `delivery.created` | Queue: `delivery-service-queue` | Consumes `delivery.created` (self-process), updates Delivery to `COMPLETED`. | Local delivery logs finalized; manual ACK sent.                                                                                    |
| **10** | **deliveryMessageService** | `delivery.success` | Exchange: `domain.events`<br>Queue: `order-service-queue` | Publishes delivery success notification. | Triggers final order closing upstream.                                                                                             |
| **11** | **reactiveOrderService** | `delivery.success` | Queue: `order-service-queue` | Consumes event and updates order status to `COMPLETED`. | Saga successfully terminates; transaction complete.                                                                                |

---

### 🔀 RabbitMQ Messaging Routing Map

This flow diagram maps how events published by the microservices are routed through RabbitMQ exchanges, routing keys, and queues to their respective consumers:

```
Publishers                    Exchange: domain.events                    Queues                     Consumers
===========                   =======================                    ======                     =========

[Order Service]  -------->  ( Routing Key: order.created )  ------>  [payment-service-queue]  ----> [Payment Service]
                                                                                                         |
                                                                                                         v (atomic outbox)
                                                                                                         
[Payment Service] ------->  ( Routing Key: payment.created ) ------> [payment-service-queue]  ----+      |
                                                                                                  |      |
                                                                                                  +------+ (self-process)

[Payment Service] ------->  ( Routing Key: payment.success ) ------> [delivery-service-queue] ----> [Delivery Service]

[Delivery Service] ------>  ( Routing Key: delivery.created ) -----> [delivery-service-queue] ----+      |
                                                                                                  |      |
                                                                                                  +------+ (self-process)

[Delivery Service] ------>  ( Routing Key: delivery.success ) ---+
                                                                 |
                                                                 v
[Payment Service]  ------>  ( Routing Key: payment.failure ) ----+-> [order-service-queue]   ----> [Order Service]
                                                                 |
[Delivery Service] ------>  ( Routing Key: delivery.failure ) ---+
                                                                 |
                                                                 +-> [payment-service-queue] ----> [Payment Service] (Revert)

-----------------------------------------------------------------------------------------------------------------

[Any Service]  ---------->  ( Routing Key: # [All Events] )  ------> [notification-service-q] ---> [Notification View]

[Failed Messages] ------->  ( Dead Letter Exchange )  -------------> [ Dead Letter Queues ]   ---> [Manual / Retry]
                            [ domain.dlx (Fanout) ]                  (e.g., payment-service-dlq)
```

---

---

## ⚙️ Microservices Overview

### 1. `reactiveOrderService`
*   **Purpose**: Manages the order lifecycle. Initiates order ingestion, handles client requests reactively, exposes an SSE stream of all orders, and streams all event bus logs.
*   **Exposed REST Endpoints**:
    *   `POST /orders`: Places a new order.
    *   `GET /orders`: Stream of order entity states (SSE).
    *   `GET /notifications`: Streams all RabbitMQ event bus messages (SSE) captured via the wildcard (`#`) queue.
    *   `GET /orders/{orderId}/details`: Orchestrated REST query resolving combined Order, Payment, and Delivery details.
*   **Database Interactions**:
    *   `orders` table: Writes order details with status `IN_PROGRESS` on order placement, and reads/updates status to `COMPLETED`/`FAILED` upon receiving downstream events.
*   **RabbitMQ Interactions**:
    *   **Publish**: Emits `order.created` events to exchange `domain.events` with routing key `order.created` (contains full order, payment, and delivery payloads).
    *   **Consume**: Listens to queue `order-service-queue` (bound to routing keys `delivery.success`, `delivery.failure`, and `payment.failure` on exchange `domain.events`).
*   👉 **[reactiveOrderService Detailed Flow Guide](reactiveOrderService/README_order_service.md)**: Conceptual guide covering the order service's reactive ingestion pipeline, listener flows, and failure recovery.

### 2. `paymentServiceAMQP`
*   **Purpose**: Processes order payments. Implements the Transactional Outbox pattern to guarantee message delivery, and uses manual RabbitMQ acknowledgments for message handling.
*   **Exposed REST Endpoints**:
    *   `GET /payments/order/{orderId}`: Retrieves payment details by order ID.
*   **Database Interactions**:
    *   `payments` table: Records payment details and updates status from `IN_PROGRESS` to `COMPLETED` during transaction authorization.
    *   `outbox_payment` table: Inserts unpublished outbox events atomically within the local payment transaction, and updates `published = true` once broker ACK is received.
*   **RabbitMQ Interactions**:
    *   **Consume**: Listens to queue `payment-service-queue` (bound to routing keys `order.created`, `payment.created`, and `delivery.failure` on exchange `domain.events`).
*   **Publish**:
    *   Outbox publisher posts `payment.created` event to exchange `domain.events` with routing key `payment.created` (polled every 2s).
    *   Post-payment handler posts `payment.success` event to exchange `domain.events` with routing key `payment.success`.
*   👉 **[paymentServiceAMQP Detailed Flow Guide](paymentServiceAMQP/README_paymentService.md)**: Conceptual guide covering the payment service's manual ACK listeners, outbox polling mechanics, self-processing, and refund handling.

### 3. `deliveryMessageService`
*   **Purpose**: Simulates a delivery gateway proxy, acting as an event-forwarding agent that reads from the delivery queue and publishes dispatches.
*   **Exposed REST Endpoints**:
    *   `GET /deliveries/order/{orderId}`: Retrieves delivery details by order ID.
*   **Database Interactions**:
    *   `delivery` table: Maps the delivery schema (persists delivery status details).
*   **RabbitMQ Interactions**:
    *   **Consume**: Listens to queue `delivery-service-queue` (bound to routing key `payment.success` on exchange `domain.events`).
    *   **Publish**: Emits `delivery.created` event to exchange `domain.events` with routing key `delivery.created` to signify shipment dispatch.

---

## 🔀 RabbitMQ Topologies

The repository includes two distinct RabbitMQ topology setups in the [doc/rabbitmq/](doc/rabbitmq) directory:

| Feature | Topology 1 ([rabbitmq_definitions.json](doc/rabbitmq/rabbitmq_definitions.json)) | Topology 2 ([rabbitmq_definitions2.json](doc/rabbitmq/rabbitmq_definitions2.json)) |
| :--- | :--- | :--- |
| **Exchange Style** | Monolithic Exchange Model | Domain-Specific Exchange Model |
| **Exchanges** | `domain.events` (Topic)<br>`domain.dlx` (Fanout) | `order.exchange` (Topic)<br>`payment.exchange` (Topic)<br>`delivery.exchange` (Topic)<br>Plus separate dead-letter exchanges (`order.dlx`, `payment.dlx`, `delivery.dlx`). |
| **Queues** | `order-service-queue`<br>`payment-service-queue`<br>`delivery-service-queue`<br>`notification-service-queue` | `order-service-queue`<br>`payment-service-queue`<br>`delivery-service-queue` |
| **Retry Strategy** | Messages published straight to Dead-Letter Queues (DLQ) upon NACK. | **Dedicated Retry Queues** (`payment-retry-queue`, `delivery-retry-queue`) with message TTL (5 mins) and Dead-Letter Routing Keys targeting domain exchanges for delayed retries. |

> [!NOTE]
> Topology 1 is loaded by default in [docker-compose.yml](doc/rabbitmq/docker-compose.yml).

### Messaging Routing Matrix (Topology 1)

Messages are routed dynamically through the topic exchange `domain.events` to the individual service queues using specific routing key patterns:

| Message Routing Key | Source Exchange | Target Queue | Subscribing Service |
| :--- | :--- | :--- | :--- |
| `order.created` | `domain.events` | `payment-service-queue` | [paymentServiceAMQP](paymentServiceAMQP) |
| `payment.created` | `domain.events` | `payment-service-queue` | [paymentServiceAMQP](paymentServiceAMQP) |
| `payment.success` | `domain.events` | `delivery-service-queue` | [deliveryMessageService](deliveryMessageService) |
| `delivery.success`<br>`delivery.failure`<br>`payment.failure` | `domain.events` | `order-service-queue` | [reactiveOrderService](reactiveOrderService) |
| `#` *(All Events)* | `domain.events` | `notification-service-queue` | *(Generic notification listener)* |

*   **Topic Exchange**: The `domain.events` exchange matches dot-separated routing keys dynamically.
*   **Wildcard Bindings**: The `notification-service-queue` uses the `#` wildcard binding key, matching zero or more routing key tokens to capture all domain events for auditing/notifications.

---

## 📬 Transactional Outbox Pattern

To guarantee at-least-once message delivery and prevent data consistency issues during database or message broker downtime, the payment service implements a Transactional Outbox pattern.

👉 For complete details on schema design, transactional rollback semantics, and idempotency, see the **[Transactional Outbox Pattern Documentation](paymentServiceAMQP/outbox_pattern.md)**.


## 📖 Operational Runbook & Verification

This section provides instructions on setting up, running, verifying, and troubleshooting the event-driven microservices.

### 📋 Prerequisites

Before running the services, ensure you have the following installed:
*   **Java Development Kit (JDK) 24**
*   **Apache Maven 3.9+**
*   **Docker & Docker Compose**

### 🚀 Environment Setup

#### 1. Start Infrastructure & UI Dashboard
First, build the static Web UI Docker image from the root directory:
```bash
docker build -t justamitsaha/rabbitmq_saga:latest ui/
```
Next, launch the containerized infrastructure and UI application using Docker Compose:
```bash
docker-compose -f doc/rabbitmq/docker-compose.yml up -d
```
*   **Web UI Dashboard**: [http://localhost:8085/](http://localhost:8085/)
*   **RabbitMQ Dashboard**: [http://localhost:15672/](http://localhost:15672/) (User: `admin` / Password: `admin`)
*   **MySQL Connection**: Port `3306` (User: `root` / Password: `shamit2020` / Database: `order_schema`)

> [!TIP]
> **Parameterizing the Backend API URL**:
> By default, the UI container connects to the backend at `http://localhost:8080`. If your backend API runs on a custom hostname/IP (e.g., in a cloud environment or separate host), you can parameterize the URL at startup:
> ```bash
> BACKEND_API_URL=http://your-backend-ip:8080 docker-compose -f doc/rabbitmq/docker-compose.yml up -d --build
> ```
> If you only want to start the database and RabbitMQ services without launching the UI container, specify the service names:
> ```bash
> docker-compose -f doc/rabbitmq/docker-compose.yml up -d mysql rabbitmq
> ```

#### 3. Build & Run Services
Compile the multi-module Maven project from the root:
```bash
mvn clean install
```
Start the microservices by running the Spring Boot application class in separate terminal sessions:

*   **reactiveOrderService** (Port `8080`):
    ```bash
    mvn -pl reactiveOrderService spring-boot:run
    ```
*   **paymentServiceAMQP** (Port `8081`):
    ```bash
    mvn -pl paymentServiceAMQP spring-boot:run
    ```
*   **deliveryMessageService** (Port `8082`):
    ```bash
    mvn -pl deliveryMessageService spring-boot:run
    ```

### 🖥️ Real-Time Web UI Dashboard

A premium, glassmorphic web dashboard is provided to visually track Saga transactions and event bus routing:

1.  **Location**: The dashboard assets are located in the [ui/](ui) directory.
2.  **Running**: Open [index.html](ui/index.html) directly in any modern web browser (no local web server required; runs via file protocol).
3.  **Features**:
    *   **Place Order Form**: Submits new orders directly to `reactiveOrderService`.
    *   **Reactive Orders List**: Streams new and updated orders reactively via WebFlux SSE.
    *   **Saga Progress Tracker**: Displays a dynamic, color-coded visual timeline updating in real time as RabbitMQ events occur.
    *   **Event Bus Live Feed**: A scrolling terminal log streaming every event captured from the RabbitMQ wildcard (`#`) queue.

### 🔍 End-to-End Verification Flows

#### 1. Place an Order (Happy Path)
Send a POST request to place a new order:
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '\''{
    "customerId": "cust-998",
    "customerName": "Alice",
    "payment": {
      "amount": 250.00,
      "paymentType": "CARD",
      "cardNo": "1234-5678-9876-5432"
    },
    "delivery": {
      "addressLine1": "123 Main St",
      "city": "Boston",
      "state": "MA",
      "postalCode": "02108"
    }
  }'\''
```

#### 2. Stream Order State Updates
Open a streaming SSE connection to listen for order state changes:
```bash
curl -N http://localhost:8080/orders
```

#### 3. Stream Real-Time Event Bus Notifications
Open a streaming SSE connection to track all background RabbitMQ events in real time:
```bash
curl -N http://localhost:8080/notifications
```

#### 3. Trigger Manual Outbox Flow
Manually create a payment for a specific order and trigger the outbox event publisher:
```bash
curl -X POST http://localhost:8081/payments/{orderId}
```

### 🛡️ Boundary Validation & Rollback Scenarios

This system enforces strict boundaries to handle partial failures and prevent inconsistent/dirty states:

#### 1. RabbitMQ Publisher Confirm Failures
*   **Failure Source**: The database write commits successfully, but the RabbitMQ broker rejects the message (NACK) or the connection goes down before acknowledgment.
*   **System Action**: In [OrderService.java](reactiveOrderService/src/main/java/com/saha/amit/orderService/service/OrderService.java#L64-L85), the publish confirm is checked. If it is a NACK or throws an error:
    1.  The local database order status is updated from `IN_PROGRESS` to `FAILED`.
    2.  An error is returned to the client.
*   **Verification**:
    1.  Stop the RabbitMQ docker container: `docker container stop rabbitmq`
    2.  Invoke the POST `/orders` endpoint. It will fail with a HTTP 500 error.
    3.  Query the orders database:
        ```sql
        USE order_schema;
        SELECT order_id, order_status FROM orders WHERE customer_id = 'cust-998';
        ```
    4.  Verify that the status of the order is marked as `FAILED`.

#### 2. Outbox Resiliency during Broker Outages
*   **Failure Source**: RabbitMQ is down when a payment is processed.
*   **System Action**: In [PaymentService.java](paymentServiceAMQP/src/main/java/com/saha/amit/orderService/paymentService/service/PaymentService.java#L64-L93), the payment is committed to the database and the outbox event is stored with `published = false`. The HTTP request succeeds, but no event is broadcast.
*   **Verification**:
    1.  Ensure RabbitMQ is running, then stop the `deliveryMessageService`.
    2.  Place an order. Payment is successfully committed.
    3.  Restart the `deliveryMessageService` later. Verify that the outbox publisher automatically delivers the message and updates `published = true` in the `outbox_payment` table once the broker confirms delivery.
    4.  Verify database state consistency:
        ```sql
        SELECT * FROM outbox_payment;
        -- Check that published is updated to 1 (true) after recovery
        ```

#### 3. Manual ACK boundaries (Ack/Nack behavior)
*   **Traditional AMQP listener**: In [RabbitListenerConfig.java](paymentServiceAMQP/src/main/java/com/saha/amit/orderService/paymentService/config/RabbitListenerConfig.java), AcknowledgeMode is configured as `MANUAL`.
*   **Processing behavior**:
    *   In [RabbitMessageListener.java](paymentServiceAMQP/src/main/java/com/saha/amit/orderService/paymentService/messaging/RabbitMessageListener.java), `channel.basicAck` is executed inside `doOnSuccess` only after the payment reactive pipeline successfully terminates.
    *   If the pipeline errors, `channel.basicNack` with `requeue=false` is executed in `doOnError`, pushing the message directly to the dead letter queue (DLQ) (`payment-service-dlq` in Topology 1) to prevent infinite loops.

---