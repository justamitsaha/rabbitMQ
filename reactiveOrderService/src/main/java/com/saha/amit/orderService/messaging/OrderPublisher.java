package com.saha.amit.orderService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

@Component
@RequiredArgsConstructor
public class OrderPublisher {

    private final Sender sender;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbit.exchange}")
    private String exchange;

    @Value("${app.rabbit.routingKey}")
    private String defaultRoutingKey;

    public <T> Mono<Void> publishEvent(String routingKey, T payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            OutboundMessage msg = new OutboundMessage(exchange, routingKey, body);

            return sender.send(Mono.just(msg))
                    .doOnSuccess(r -> System.out.println("✅ Published event [" + routingKey + "] to exchange [" + exchange + "]"))
                    .doOnError(e -> System.err.println("❌ Failed to publish: " + e.getMessage()));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public <T> Mono<Void> publishEvent(T payload) {
        return publishEvent(defaultRoutingKey, payload);
    }
}
