package com.saha.amit.orderService.repository;

import com.saha.amit.orderService.domain.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface OrderRepository extends ReactiveCrudRepository<Order, String> {
    Mono<Order> findById(String id);
}
