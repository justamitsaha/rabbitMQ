package com.saha.amit.orderService.paymentService.repository;

import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OutboxRepository extends ReactiveCrudRepository<OutboxEvent, Long> {
    Flux<OutboxEvent> findByPublishedFalse();
}
