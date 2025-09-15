package com.saha.amit.orderService.paymentService.repository;

import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import com.saha.amit.orderService.paymentService.domain.Payment;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class CustomRepositoryImpl implements CustomRepository {

    private final DatabaseClient databaseClient;

    public CustomRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<OutboxEvent> insertToOutbox(OutboxEvent outboxEvent) {
        String sql = """
                INSERT INTO outbox
                (id,
                aggregate_id,
                aggregate_type,
                event_type,
                payload,
                created_at,
                published)
                VALUES
                (:id,
                :aggregate_id,
                :aggregate_type,
                :event_type,
                :payload,
                :created_at,
                :published);
                """;
        return databaseClient.sql(sql)
                .bind("id", outboxEvent.getId())
                .bind("aggregate_id", outboxEvent.getAggregateId())
                .bind("aggregate_type", outboxEvent.getAggregateType())
                .bind("event_type", outboxEvent.getEventType())
                .bind("payload", outboxEvent.getPayload())
                .bind("created_at", outboxEvent.getCreatedAt())
                .bind("published", outboxEvent.getPublished())
                .fetch()
                .rowsUpdated()
                .thenReturn(outboxEvent);
    }

    @Override
    public Mono<Payment> insertToPayment(Payment payment) {
        String sql = """
                INSERT INTO payments
                (id,
                order_id,
                status,
                created_at)
                VALUES
                (:id,
                :order_id,
                :status,
                :created_at);
                """;
        return databaseClient.sql(sql)
                .bind("id", payment.getId())
                .bind("order_id", payment.getId())
                .bind("status", payment.getStatus())
                .bind("created_at", payment.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(payment);
    }


}
