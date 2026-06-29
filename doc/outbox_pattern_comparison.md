# Transactional Outbox Pattern: Architectures & Trade-offs

This document provides a conceptual analysis of the **Transactional Outbox Pattern**, comparing different implementation strategies and examining event-publishing trade-offs.

---

## 📬 Implementation Strategies

Once outbox rows are written to the database inside the business transaction, a relay must publish them. There are three primary strategies to run the outbox relay:

### 1. Polling Publisher (Used in Project)
A scheduler queries the outbox table periodically (e.g. every 500ms) for unsent events, publishes them, and updates their status.
*   **Pros**: Simple to build, database-agnostic, and runs directly inside the application process.
*   **Cons**: Introduces polling queries even when there are no new events; creates event delivery latency equal to the polling interval.

### 2. Transaction Log Tailer (Change Data Capture / CDC)
An external engine (e.g., Debezium) tails the database transaction logs, automatically captures inserts to the outbox table, publishes them to the broker, and deletes/moves them.
*   **Pros**: Real-time publishing with zero database query overhead; decouples application code from publishing logic.
*   **Cons**: Significant operational overhead (requires Kafka Connect, ZooKeeper/KRaft, and CDC connectors).

### 3. Insert + Notify Pattern
The database fires an event notification (e.g., PostgreSQL `NOTIFY` or MySQL triggers) upon outbox table inserts. The application listens to this channel and immediately publishes the event.
*   **Pros**: Real-time event publishing without database polling loops.
*   **Cons**: Couplesto specific database engines; lacks portability across testing databases (like H2).

---

## ⚖️ Outbox Pattern vs. Dead Letter Queue (DLQ)

The Outbox pattern and Dead Letter Queues solve reliability issues at opposite ends of the message broker pipeline:

*   **Transactional Outbox** solves **Producer-Side Reliability**:
    *   *Question*: "Did the event successfully reach the broker?"
    *   *Failure*: The database commits but the broker is down or unreachable.
*   **Dead Letter Queue (DLQ)** solves **Consumer-Side Reliability**:
    *   *Question*: "Can the consumer successfully process this received message?"
    *   *Failure*: The consumer receives a message but crashes during processing, or throws a serialization error.

Without an Outbox, a DLQ cannot prevent data loss because it only captures messages that successfully made it to the broker in the first place.

---

## 🏎️ Comparative Analysis: Event Delivery Patterns

When initiating state changes in an event-driven system, there are three primary design options:

### 1. DB-First (Classic Outbox)
Save the entity and outbox event in a single transaction, then publish asynchronously via a poller or CDC.
*   **Pros**: Strong local consistency, works offline when the broker is down.
*   **Cons**: Polling latency, requires managing the outbox table.
*   **Best For**: Core business and transactional systems.

### 2. Event-First (Publish & Self-Consume)
Publish the event to the broker directly, then have the service consume its own event to perform the local database write.
*   **Pros**: Low latency, matches pure Event Sourcing principles.
*   **Cons**: Risk of data loss if the broker is down; local reads are eventually consistent.
*   **Best For**: Systems designed around Event Sourcing and read-model projections.

### 3. Hybrid (Write Outbox only, Self-Consume)
Write only the outbox row to the database, publish it via a poller, and then consume the event to update the main business tables.
*   **Pros**: Replayability from outbox events.
*   **Cons**: Slowest local database state updates, higher broker traffic, complex error handling.
*   **Best For**: Transition states when migrating legacy databases to Event Sourcing.

### 📊 Latency & Trade-offs Summary

| Approach | API Response | Event Publish | Local DB State | Best For |
| :--- | :--- | :--- | :--- | :--- |
| **DB-First (Classic Outbox)** | ⚡ Fast (immediate DB write) | ⏱️ Delayed by poll interval | ⚡ Immediate | Traditional transactional systems |
| **Event-First** | ⚡ Fast (broker publish) | ⚡ Immediate | ⏱️ Eventually consistent | Pure Event Sourcing |
| **Hybrid** | ⚡ Fast (outbox DB write) | ⏱️ Delayed by poll interval | 🐢 Slowest (hops: poll + consume) | Gradual Event Sourcing migrations |
