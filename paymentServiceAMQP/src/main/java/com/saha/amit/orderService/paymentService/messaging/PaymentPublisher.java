package com.saha.amit.orderService.paymentService.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbit.paymentExchange}")
    private String exchange;

    public void publish(String routingKey, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            System.out.println("✅ Published to exchange [" + exchange + "] key [" + routingKey + "]");
        } catch (Exception e) {
            System.err.println("❌ Failed to publish: " + e.getMessage());
        }
    }
}
