package com.saha.amit.orderService.paymentService.repository;

import com.saha.amit.orderService.paymentService.domain.Payment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, String> {
    Mono<Payment> findByOrderId(String orderId);
}
