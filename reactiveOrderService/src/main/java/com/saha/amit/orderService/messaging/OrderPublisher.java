package com.saha.amit.orderService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.OutboundMessageResult;
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

    Logger logger = LoggerFactory.getLogger(OrderPublisher.class);

    public <T> Mono<Boolean> publishEvent(String routingKey, T payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            OutboundMessage msg = new OutboundMessage(exchange, routingKey, body);

            return sender.sendWithPublishConfirms(Mono.just(msg))
                    .next()
                    .doOnNext(confirm -> {
                        if (confirm.isAck()) {
                            logger.info("✅ Broker ACK for : {} event to exchange : {}", routingKey, exchange);
                        } else {
                            logger.info("❌ Broker NACK for : {} event: returned=: {}", routingKey, confirm.isReturned());
                        }
                    })
                    .map(OutboundMessageResult::isAck); // complete when confirms processed

            /*return sender.sendWithPublishConfirms(Mono.just(msg)) // ✅ publisher confirms
                    .doOnNext(confirm -> {
                        if (confirm.isAck()) {
                            System.out.printf("✅ Broker ACK for [%s] event to exchange [%s]%n", routingKey, exchange);
                        } else {
                            System.err.printf("❌ Broker NACK for [%s] event: returned=%s%n", routingKey, confirm.isReturned());
                        }
                    })
                    .then(); // complete when confirms processed*/
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public <T> Mono<Boolean> publishEvent(T payload) {
        return publishEvent(defaultRoutingKey, payload);
    }
}
