# Domain-Driven Design (DDD) Basics

This document outlines the core concepts of **Domain-Driven Design (DDD)** and how they align with event-driven microservices.

---

## 🧭 Core DDD Philosophy

Domain-Driven Design (DDD) is an approach to software development for complex business requirements. It places the focus of the system design on the **business domain** and aligns the code structure directly with business concepts.

*   **Ubiquitous Language**: A shared, common language defined collaboratively by developers, business analysts, and domain experts. This language is used consistently in conversation, requirements, and codebase (class names, database schemas, API properties).
*   **Bounded Context**: A boundary within which a particular domain model applies. In a microservices architecture, a bounded context typically maps directly to a single microservice (e.g., Order Bounded Context vs. Payment Bounded Context).

---

## 🧱 Key Tactical Patterns

DDD defines several patterns to organize code within a bounded context:

### 1. Entities
Objects defined by a unique, persistent identity rather than their attributes. Two entities with different properties but the same ID are considered the same object.
*   *Project Example*: `Order` is an entity because it has a unique `orderId` that defines its identity across updates.

### 2. Value Objects
Immutable objects defined by their attributes rather than a unique identity. If two value objects have identical attributes, they are considered equal.
*   *Project Example*: `Address` (with `addressLine1`, `city`, `state`, `postalCode`) is a value object. If two addresses have the exact same fields, they are equal.

### 3. Aggregates
A cluster of associated entities and value objects treated as a single transaction boundary unit.
*   **Aggregate Root**: The parent entity that guards access to the aggregate. External services can only reference the Aggregate Root by its ID; they cannot modify internal child entities directly.
*   *Project Example*: `Order` is the Aggregate Root. If there were child items like `OrderItem`, they would be accessed and modified exclusively through the `Order` aggregate root to ensure business invariants are maintained.

### 4. Repositories
Provide collection-like access to aggregates, abstracts database access, and handles persistence boundaries.

### 5. Domain Events
Objects that describe something significant that happened in the business domain (expressed in past tense, e.g. `OrderPlaced`, `PaymentFailed`). 
*   In microservices, domain events are published to message brokers to trigger actions in other bounded contexts.

---

## 🔗 DDD, Outbox, and Saga: How They Connect

In large-scale enterprise systems, DDD, the Transactional Outbox pattern, and Sagas work together:

1.  **DDD (Structure)**: Defines the boundaries (Bounded Contexts) and structures the code into aggregates (e.g., Order Aggregate, Payment Aggregate).
2.  **Outbox Pattern (Reliability)**: Ensures that when an aggregate changes (e.g., `Order` changes status and emits an event), the database commit and the event publication are atomic.
3.  **Saga Pattern (Coordination)**: Manages the business transactions that cross Bounded Contexts (e.g., Order Service -> Payment Service -> Delivery Service).
