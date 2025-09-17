package com.saha.amit.orderService.service;

import com.saha.amit.orderService.domain.Order;
import com.saha.amit.orderService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.dto.Status;
import com.saha.amit.orderService.messaging.OrderPublisher;
import com.saha.amit.orderService.repository.CustomOrderRepository;
import com.saha.amit.orderService.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final OrderPublisher orderPublisher;
    private final CustomOrderRepository customOrderRepository;

    public Mono<Order> placeOrder(PlaceOrderRequest req) {
        String orderId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .orderId(orderId)
                .customerId(req.getCustomerId())
                .customerName(req.getCustomerName())
                .orderStatus(Status.PENDING)
                .createdAt(Instant.now())
                .build();
        logger.info("Placing order for customer: {}", order);
        /*return orderPublisher.publishEvent(order)
                .filter(published -> published) // proceed only if published is true
                .flatMap(published -> customOrderRepository.insert(order))
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to publish order.created event")))
                .flatMap(Mono::just);*/


        return customOrderRepository.insert(order)
                .flatMap(savedOrder ->
                        orderPublisher.publishEvent(savedOrder)
                                .flatMap(ack -> {
                                    if (ack) {
                                        return Mono.just(savedOrder);
                                    } else {
                                        // mark order as publish_failed for retry
                                        savedOrder.setOrderStatus(Status.REJECTED);
                                        return orderRepository.save(savedOrder)
                                                .then(Mono.error(new RuntimeException("Failed to publish order.created")));
                                    }
                                })
                );
    }

    public Flux<Order> findAllOrders() {
        return orderRepository.findAll();
    }


    public Mono<Order> updateOrderStatus(String orderId, Status status) {
        return orderRepository.findById(orderId)
                .flatMap(o -> {
                    o.setOrderStatus(status);
                    return orderRepository.save(o);
                });
    }
}
