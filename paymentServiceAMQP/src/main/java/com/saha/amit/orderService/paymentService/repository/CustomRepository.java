package com.saha.amit.orderService.paymentService.repository;

import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import com.saha.amit.orderService.paymentService.domain.Payment;
import reactor.core.publisher.Mono;

public interface CustomRepository {

    Mono<OutboxEvent> insertToOutbox(OutboxEvent outboxEvent);

    Mono<Payment> insertToPayment(Payment payment);
}
