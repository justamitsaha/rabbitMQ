package com.saha.amit.orderService.service;

import com.saha.amit.orderService.domain.Order;
import com.saha.amit.orderService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.dto.Status;
import com.saha.amit.orderService.messaging.OrderPublisher;
import com.saha.amit.orderService.repository.CustomOrderRepository;
import com.saha.amit.orderService.repository.OrderRepository;
import com.saha.amit.orderService.dto.PaymentDto;
import com.saha.amit.orderService.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.rabbit.exchange:domain.events}")
    private String exchange;

    @Value("${app.rabbit.routingKey:order.created}")
    private String placeOrderRoutingKey;

    @Value("${app.security.crypto-key}")
    private String cryptoKey;

    /**
     * Initiates the order placement flow by persisting the order in status IN_PROGRESS 
     * and publishing the corresponding order.created event to RabbitMQ.
     */
    public Mono<Order> placeOrder(PlaceOrderRequest placeOrderRequest) {
        String orderId = UUID.randomUUID().toString();
        MDC.put("correlationId", orderId);
        Order order = Order.builder()
                .orderId(orderId)
                .customerId(placeOrderRequest.getCustomerId())
                .customerName(placeOrderRequest.getCustomerName())
                .orderStatus(Status.IN_PROGRESS)
                .createdAt(Instant.now())
                .build();
        placeOrderRequest.setOrderId(orderId);
        placeOrderRequest.setOrderStatus(order.getOrderStatus());
        placeOrderRequest.setCreatedAt(order.getCreatedAt());
        placeOrderRequest.getPayment().setOrderId(orderId);
        placeOrderRequest.getDelivery().setOrderId(orderId);

        // Encrypt sensitive payment credentials before broadcasting on event bus
        PaymentDto payment = placeOrderRequest.getPayment();
        if (payment != null) {
            payment.setCardNo(CryptoUtil.encrypt(payment.getCardNo(), cryptoKey));
            payment.setAccountNo(CryptoUtil.encrypt(payment.getAccountNo(), cryptoKey));
            payment.setUpiId(CryptoUtil.encrypt(payment.getUpiId(), cryptoKey));
        }

        logger.info("Preparing Order data to be saved in database: {}", order);
        logger.info("Order request before getting sent to rabbit mq: {}", placeOrderRequest);

        /*
        RabbitMQ can't be source of truth we should save the transaction in db first then publish to MQ
        return orderPublisher.publishEvent(order)
                .filter(published -> published) // proceed only if published is true
                .flatMap(published -> customOrderRepository.insert(order))
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to publish order.created event")))
                .flatMap(Mono::just);
        */


        return customOrderRepository.insert(order)
                .flatMap(savedOrder ->
                        orderPublisher.publishEvent(exchange, placeOrderRoutingKey, placeOrderRequest, orderId)
                                .flatMap(ack -> {
                                    if (ack) {
                                        return Mono.just(savedOrder);
                                    } else {
                                        // mark order as publish_failed for retry
                                        savedOrder.setOrderStatus(Status.FAILED);
                                        return orderRepository.save(savedOrder)
                                                .then(Mono.error(new RuntimeException("Failed to publish order.created")));
                                    }
                                })
                                .doOnNext(order1 -> logger.info("Order published to exchange: {}, with roting key: {}, and data: {}",exchange, placeOrderRoutingKey, order1))
                                .doOnError(err ->
                                        {
                                            logger.error("Error publishing order: {} error details: {}", orderId, err.getMessage());
                                            updateOrderStatus(orderId, Status.FAILED)
                                                    .subscribe();
                                        }
                                )
                );
    }

    /**
     * Retrieves all orders persisted in the database.
     */
    public Flux<Order> findAllOrders() {
        return orderRepository.findAll();
    }


    /**
     * Updates the status of an order in the database by its order ID.
     */
    public Mono<Order> updateOrderStatus(String orderId, Status status) {
        return orderRepository.findById(orderId)
                .flatMap(o -> {
                    o.setOrderStatus(status);
                    logger.info("Updating order status to {} for order{}", status, o);
                    return orderRepository.save(o);
                });
    }
}
