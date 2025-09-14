package com.saha.amit.orderService.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.service.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.rabbitmq.Receiver;
import reactor.core.Disposable;



@Component
@RequiredArgsConstructor
public class RabbitListeners {

    private final Receiver receiver;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private Disposable subscription;

    @PostConstruct
    public void start() {
        // Listen to the order-service-queue
        subscription = receiver.consumeAutoAck("order-service-queue")
                .flatMap(delivery -> {
                    String routingKey = delivery.getProperties().getType(); // or delivery.getEnvelope().getRoutingKey()
                    byte[] body = delivery.getBody();
                    try {
                        JsonNode payload = objectMapper.readTree(body);
                        String orderId = payload.get("orderId").asText();
                        if ("delivery.completed".equals(delivery.getProperties().getType()) ||
                                "delivery.completed".equals(delivery.getEnvelope().getRoutingKey())) {
                            return orderService.updateOrderStatus(orderId, com.saha.amit.orderService.domain.OrderStatus.CLOSED);
                        } else if ("payment.failed".equals(delivery.getProperties().getType()) ||
                                "payment.failed".equals(delivery.getEnvelope().getRoutingKey())) {
                            return orderService.updateOrderStatus(orderId, com.saha.amit.orderService.domain.OrderStatus.CANCELLED);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return reactor.core.publisher.Mono.empty();
                })
                .subscribe();
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) subscription.dispose();
    }
}
