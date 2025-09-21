package com.saha.amit.orderService.paymentService.repository;

import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import com.saha.amit.orderService.paymentService.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class CustomRepositoryImpl implements CustomRepository {

    private final DatabaseClient databaseClient;
    private static final Logger logger = LoggerFactory.getLogger(CustomRepositoryImpl.class);

    public CustomRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<OutboxEvent> insertToOutbox(OutboxEvent outboxEvent) {
        logger.info("Inserting outbox event: {}", outboxEvent);
        String sql = """
            INSERT INTO outbox_payment
            (payment_id,
             aggregate_id,
             aggregate_type,
             event_type,
             payload,
             created_at,
             published)
            VALUES
            (:payment_id,
             :aggregate_id,
             :aggregate_type,
             :event_type,
             :payload,
             :created_at,
             :published);
            """;

        return databaseClient.sql(sql)
                .bind("payment_id", outboxEvent.getPaymentId())
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
        logger.info("Inserting payment: {}", payment);
        String sql = """
                INSERT INTO payments
                (payment_id,
                 order_id,
                 payment_status,
                 amount,
                 payment_type,
                 card_no,
                 account_no,
                 upi_id,
                 created_at)
                VALUES
                (:payment_id,
                 :order_id,
                 :payment_status,
                 :amount,
                 :payment_type,
                 :card_no,
                 :account_no,
                 :upi_id,
                 :created_at);
                """;

        return databaseClient.sql(sql)
                .bind("payment_id", payment.getPaymentId())
                .bind("order_id", payment.getOrderId())
                .bind("payment_status", payment.getPaymentStatus()) // Enum → String
                .bind("amount", payment.getAmount())
                .bind("payment_type", payment.getPaymentType())     // Enum → String
                .bind("card_no", payment.getCardNo())
                .bind("account_no", payment.getAccountNo())
                .bind("upi_id", payment.getUpiId())
                .bind("created_at", payment.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(payment);
    }


}
