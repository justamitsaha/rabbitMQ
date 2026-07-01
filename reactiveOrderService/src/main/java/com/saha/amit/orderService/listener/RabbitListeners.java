package com.saha.amit.orderService.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.dto.Status;
import com.saha.amit.orderService.service.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.rabbitmq.Receiver;
import reactor.core.Disposable;



@Component
@RequiredArgsConstructor
public class RabbitListeners {

    private static final Logger logger = LoggerFactory.getLogger(RabbitListeners.class);
    private final Receiver receiver;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private Disposable subscription;

    /**
     * Initializes the queue consumer subscription on startup to listen and react 
     * to downstream payment and delivery events.
     */
    @PostConstruct
    public void start() {
        // Listen to the order-service-queue
        subscription = receiver.consumeAutoAck("order-service-queue")
                .flatMap(delivery -> {
                    String receivedKey = delivery.getEnvelope().getRoutingKey();
                    String typeProp = delivery.getProperties().getType();
                    byte[] body = delivery.getBody();
                    try {
                        JsonNode payload = objectMapper.readTree(body);
                        String orderId = payload.get("orderId").asText();

                        if ("delivery.completed".equals(typeProp) ||
                                "delivery.completed".equals(receivedKey) ||
                                "delivery.success".equals(typeProp) ||
                                "delivery.success".equals(receivedKey)) {
                            return orderService.updateOrderStatus(orderId, Status.COMPLETED);
                        } else if ("payment.failed".equals(typeProp) ||
                                "payment.failed".equals(receivedKey) ||
                                "payment.failure".equals(typeProp) ||
                                "payment.failure".equals(receivedKey) ||
                                "delivery.failed".equals(typeProp) ||
                                "delivery.failed".equals(receivedKey) ||
                                "delivery.failure".equals(typeProp) ||
                                "delivery.failure".equals(receivedKey)) {
                            logger.warn("❌ Saga failed for orderId={}, marking order status as FAILED", orderId);
                            return orderService.updateOrderStatus(orderId, Status.FAILED);
                        }
                    } catch (Exception e) {
                        logger.error("Error processing delivery: {}", e.getMessage(), e);
                    }

                    return reactor.core.publisher.Mono.empty();
                })
                .subscribe();
     }

    /**
     * Disposes of the active RabbitMQ event subscription when the bean is destroyed.
     */
    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) subscription.dispose();
    }
}
