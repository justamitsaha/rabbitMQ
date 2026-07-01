package com.saha.amit.repository;

import com.saha.amit.domain.Delivery;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, String> {
    Mono<Delivery> findByOrderId(String orderId);
}
