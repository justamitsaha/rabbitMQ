package com.saha.amit.orderService.controller;

import com.saha.amit.orderService.domain.Order;
import com.saha.amit.orderService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.dto.OrderDetailsDto;
import com.saha.amit.orderService.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CrossOrigin
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    Logger logger = LoggerFactory.getLogger(OrderController.class);

    /**
     * REST endpoint to place a new order. Persists the order locally and triggers payment processing.
     */
    @PostMapping
    public Mono<ResponseEntity<Order>> placeOrder(@RequestBody PlaceOrderRequest req) {
        logger.info("Received order placement request: {}", req);
        return orderService.placeOrder(req)
                .map(ResponseEntity::ok)
                .doOnError(ex -> logger.error("Error placing order: {}, correlationID: {}, ", ex.getMessage(), req.getOrderId()))
                .onErrorResume(ex -> {
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                     .body(null) // or send an error DTO
                    );
                });
    }


    /**
     * REST endpoint to retrieve a streaming SSE (Server-Sent Events) feed of all placed orders.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Order> findAllOrders() {
        return this.orderService.findAllOrders();
    }

    /**
     * REST endpoint to orchestrate calls and retrieve combined Order details, Payment details, and Delivery details.
     */
    @GetMapping("/{orderId}/details")
    public Mono<ResponseEntity<OrderDetailsDto>> getOrderDetails(@PathVariable String orderId) {
        logger.info("Received request to fetch combined details for orderId: {}", orderId);
        return orderService.getOrderDetails(orderId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}

