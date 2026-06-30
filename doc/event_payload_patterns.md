# Event Payload Design Patterns

In distributed event-driven systems (like this Saga Choreography implementation), designing the structure and contents of event payloads is a critical architectural decision. This document details the three primary event payload patterns, analyzes their trade-offs, and provides recommendations for handling polymorphic payment schemas (CARD, UPI, NETBANKING, and COD) securely.

---

## 🏛️ Payload Design Patterns

```
                                  [EVENT PAYLOAD PATTERNS]
                                             |
     +---------------------------------------+---------------------------------------+
     |                                       |                                       |
     v                                       v                                       v
[Event-Carried State Transfer]      [Event Notification]                   [Claim Check / Token]
- Full entity payload inside        - Minimal ID & event metadata          - References a secure token
  the message body.                   in the message body.                   stored in a database/vault.
- Zero callback coupling.            - Requires API callbacks.              - Asynchronous and secure.
```

---

### 1. Event-Carried State Transfer (ECST)
The publisher bundles the complete, rich state of the resource inside the event payload. Consumers obtain all the information they need to perform their actions directly from the message body.

*   **Project Context**: This is the default pattern used when `reactiveOrderService` publishes `order.created` containing full address details and payment payloads.
*   **Pros**:
    *   **Zero Temporal Coupling**: Consumers (Payment & Delivery) do not need to call the Order Service. They can process events independently even if the Order Service is offline.
    *   **Optimal Throughput**: Eliminates HTTP/gRPC roundtrips to retrieve metadata, reducing network latency.
*   **Cons**:
    *   **Security Risks**: Sensitive data (such as raw credit card numbers or bank account details) is broadcast over the event bus, making it visible to all listeners (including auditors and logger wildcard queues).
    *   **Schema Bloat**: Events carry heavy metadata that many consumers do not require.

---

### 2. Event Notification
The publisher broadcasts a lightweight event containing only the resource identifier (e.g. `orderId`) and the transition state. Consumers are notified that "something happened" and must call back to the source system via REST/gRPC to fetch the specific data they need.

*   **Pros**:
    *   **High Security**: Sensitive details are never written to the event broker; they are pulled securely over direct, authenticated channels.
    *   **Schema Stability**: The message payload is small and rarely changes when business requirements evolve.
*   **Cons**:
    *   **Tight Coupling**: Downstream services are dependent on the availability of the Order Service. If the Order Service is down, processing halts.
    *   **API Traffic Storm**: Downstream services generate a burst of query requests back to the Order Service database for every event consumed.

---

### 3. Claim Check / Tokenization Pattern
The publisher saves the sensitive or heavy payload in a secure database, cache (like Redis), or encrypted vault, and passes a secure reference ID (a "Claim Check" or token) in the event. The consumer uses the token to resolve the data from the secure storage.

*   **Pros**:
    *   **Secure & Scalable**: Keeps events small, prevents PCI-DSS compliance leaks, and allows asynchronous processing.
*   **Cons**:
    *   **Infrastructure Overhead**: Requires setting up and maintaining a shared secure storage or token vault.

---

## 📊 Comparative Trade-Off Matrix

| Metric | Event-Carried State Transfer | Event Notification | Claim Check / Tokenization |
| :--- | :--- | :--- | :--- |
| **Temporal Coupling** | **Low** (Decoupled) | **High** (Tight) | **Low** (Decoupled) |
| **Network Overhead** | **Low** (Single push) | **High** (Event + API query) | **Medium** (Event + Vault check) |
| **Payload Size** | Large | Tiny | Small |
| **Data Leakage Risk** | **High** (Raw credentials on bus) | **Low** (Only IDs on bus) | **Low** (Only secure token on bus) |
| **Schema Evolution** | Complex (Changes affect all) | Simple (Rarely changes) | Medium |

---

## 🎨 Application to the Polymorphic Payment Schema

When implementing a schema supporting **4 payment types** (CARD, UPI, NETBANKING, COD), the data structure of the event payload varies:

1.  **CARD**: Requires `card_no`.
2.  **UPI**: Requires `upi_id`.
3.  **NETBANKING**: Requires `account_no`.
4.  **COD (Cash on Delivery)**: Requires no credentials.

### 🔒 Architectural Recommendation

To support this polymorphically while maintaining compliance and security, a **Hybrid Approach** is recommended:

```
[Client App] ---> [Order Service] --(Tokenize/Encrypt)--> [Secure Vault]
                        |
                        +---(Publish order.created)---> [Event Bus]
                                                           |
                                                           | (Payload contains: address, 
                                                           |  paymentType, and paymentToken)
                                                           v
                                                    [Payment Service] --(Resolve Token)--> [Secure Vault]
```

1.  **Use Event-Carried State for Addresses**: Shipping address details are non-sensitive and read-only. Include them directly in the event to keep `deliveryMessageService` decoupled.
2.  **Use Tokenization for Payments**: The `reactiveOrderService` should not include raw `card_no` or `account_no` in the event. Instead:
    *   For **COD**: The payment payload in the event contains only `"paymentType": "COD"`.
    *   For **CARD / UPI / NETBANKING**: The event contains only `"paymentType": "CARD"` and a secure `"paymentToken": "tok_xxxx"`.
    *   The `paymentServiceAMQP` resolves the token securely through a vault API to charge the customer.

---

## 🔗 Related Resources
*   [RabbitMQ Enterprise Concepts Guide](rabbitmq_concepts.md): Details exchange topologies and wildcard routing keys.
*   [Saga Choreography Pattern](saga_pattern.md): Illustrates the sequence of events across microservice boundaries.
*   [Root Project README](../README.md): Operational guide for building and executing the microservices.
