package com.saha.amit.orderService.controller;

import com.saha.amit.orderService.domain.Order;
import com.saha.amit.orderService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Mono<ResponseEntity<Order>> placeOrder(@RequestBody PlaceOrderRequest req) {
        return orderService.placeOrder(req)
                .map(ResponseEntity::ok);
    }


    @GetMapping( produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Order> findAllOrders() {
        return this.orderService.findAllOrders();
    }
}

