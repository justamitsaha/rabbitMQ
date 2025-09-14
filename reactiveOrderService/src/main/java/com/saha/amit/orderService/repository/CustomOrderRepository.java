package com.saha.amit.orderService.repository;

import com.saha.amit.orderService.domain.Order;
import reactor.core.publisher.Mono;

public interface CustomOrderRepository {
    Mono<Order> insert(Order order);
}
