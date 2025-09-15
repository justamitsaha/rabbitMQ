package com.saha.amit.orderService.paymentService.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table("outbox")
public class OutboxEvent {
    @Id
    private String id;              // primary key for outbox row
    private String aggregateId;   // business entity ID (e.g., paymentId, orderId)
    private String aggregateType; // type of entity (Payment, Order, Delivery, etc.)
    private String eventType;     // event name (payment.created, payment.completed, etc.)
    private String payload;       // JSON payload (the actual event data)
    private Instant createdAt;    // when this outbox entry was created
    private Boolean published;    // has it been sent to RabbitMQ yet?
}
