# Saga Pattern: Distributed Transactions & Choreography

This document covers the **Saga Pattern** for distributed transactions, comparing coordination strategies and detailing the system's business transaction flow.

---

## 🛠️ The Saga Pattern Concept

In microservices, business transactions often span multiple services. Because traditional distributed transactions (like 2PC) are slow and lock resources, the Saga pattern is used instead.

A **Saga** is a sequence of local transactions. Each local transaction updates the database and publishes an event or message to trigger the next transaction in the sequence. 

If a local transaction fails because it violates business rules, then the saga executes a series of **compensating transactions** that undo the changes made by the preceding transactions.

```
[Service A] --(Local Tx 1)--> [Event A] ---> [Service B] --(Local Tx 2)--> [Event B] ...
  (If failure occurs downstream)
[Service B] --(Compensating Tx 2)--> [Service A] --(Compensating Tx 1)--> [Restored State]
```

---

## 🔀 Saga Coordination: Choreography vs. Orchestration

Sagas can be coordinated in two different ways:

### 1. Choreography-Based Saga (Used in Project)
There is no central coordinator. Each service listens to events from other services and decides which local transaction to execute next.
*   **Pros**: Highly decoupled, easy to implement for simple flows, no single point of failure.
*   **Cons**: Hard to understand the entire workflow visually; risk of cyclic dependencies.

### 2. Orchestration-Based Saga
A central controller (Orchestrator) manages the transaction flow. It tells the participant services which local transactions to execute and in what order.
*   **Pros**: Centralized view of the entire workflow; avoids cyclic dependencies.
*   **Cons**: Risk of concentrating too much business logic in the orchestrator; single point of failure if the orchestrator goes down.

---

## 🔄 Business Transaction Flow (Order Placement)

The application flow for order placement in this project is executed using a Choreography Saga coordinated via RabbitMQ events:

```
order.created [Order Service]
 └─ payment.created [Payment Service]
     ├─ payment.success [Payment Service]
     │   └─ delivery.created [Delivery Service]
     │       ├─ delivery.success [Delivery Service]
     │       │   └─ order.COMPLETED [Order Service]
     │       └─ delivery.failure [Delivery Service]
     │           ├─ order.FAILED [Order Service]
     │           └─ refund.completed [Payment Service] (Compensating)
     └─ payment.failure [Payment Service]
         └─ order.FAILED [Order Service] (Compensating)
```

### 1. Order Creation
*   **Action**: Client sends a request to the Order Service.
*   **Local Transaction**: Order Service saves the order as `IN_PROGRESS` and publishes `order.created`.
*   **Routing**: RabbitMQ routes this to the `payment-service-queue` and `notification-service-queue`.

### 2. Payment Flow
*   **Action**: Payment Service consumes `order.created`.
*   **Local Transaction**: Saves a Payment record as `IN_PROGRESS`, commits it alongside a `payment.created` outbox event.
*   **Trigger**: An outbox poller publishes `payment.created` to `payment-service-queue` (for self-processing).
*   **Execution**: Payment Service consumes `payment.created` and charges the card.
    *   **Success**: Payment status is updated to `COMPLETED` and `payment.success` is published.
    *   **Failure**: Payment status is updated to `FAILED` and `payment.failure` is published.

### 3. Delivery Flow
*   **Action**: Delivery Service consumes `payment.success`.
*   **Local Transaction**: Saves a Delivery record as `IN_PROGRESS` and publishes `delivery.created`.
*   **Execution**: Consumer processes shipment.
    *   **Success**: Delivery status is updated to `COMPLETED` and `delivery.success` is published.
    *   **Failure**: Delivery status is updated to `FAILED` and `delivery.failure` is published.

### 4. Compensation / Failure Rollbacks
*   **Payment Failure**: If `payment.failure` is published, the Order Service consumes it and executes a compensating transaction updating order status to `FAILED`.
*   **Delivery Failure**: If `delivery.failure` is published:
    *   **Order Service**: Consumes it and updates order status to `FAILED`.
    *   **Payment Service**: Consumes it, initiates refund logic, and updates status to `REFUND`.

---

## 🕵️‍♂️ Key Saga Design Challenges

*   **Observability**: It is difficult to track the current state of a transaction spanning multiple databases. Distributed tracing (`Correlation ID` headers propagated in messages) is critical.
*   **Idempotency**: Message brokers guarantee at-least-once delivery, meaning consumers can receive duplicate events. Consumers must be idempotent (e.g., checking if the order status is already updated before writing).
*   **Compensating failures**: What if a compensating transaction fails? You need robust retry policies, alerting, and human-in-the-loop manual escalation scripts.
