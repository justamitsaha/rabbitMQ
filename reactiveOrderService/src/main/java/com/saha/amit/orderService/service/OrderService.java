package com.saha.amit.orderService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.domain.Order;
import com.saha.amit.orderService.domain.OrderStatus;
import com.saha.amit.orderService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.messaging.OrderPublisher;
import com.saha.amit.orderService.repository.CustomOrderRepository;
import com.saha.amit.orderService.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderPublisher orderPublisher;
    private final CustomOrderRepository customOrderRepository;

    public Mono<Order> placeOrder(PlaceOrderRequest req) {
        String orderId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .id(orderId)
                .customerId(req.getCustomerId())
                .status(OrderStatus.PAYMENT_PENDING)
                .createdAt(Instant.now())
                .build();

        /*
        We can use orderRepository.save(order) here but when we provide key ReactiveCrudRepository
        thinks its am update and it would trigger an update instead of insert.
         */
        return customOrderRepository.insert(order)
                .flatMap(savedOrder -> orderPublisher.publishEvent(savedOrder)
                        .thenReturn(savedOrder));
    }

    public Flux<Order> findAllOrders() {
        return orderRepository.findAll();
    }



    public Mono<Order> updateOrderStatus(String orderId, OrderStatus status) {
        return orderRepository.findById(orderId)
                .flatMap(o -> {
                    o.setStatus(status);
                    return orderRepository.save(o);
                });
    }
}
