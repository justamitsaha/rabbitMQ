package com.saha.amit.orderService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.OutboundMessageResult;
import reactor.rabbitmq.Sender;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderPublisher {

    private final Sender sender;
    private final ObjectMapper objectMapper;


    Logger logger = LoggerFactory.getLogger(OrderPublisher.class);

    public <T> Mono<Boolean> publishEvent(String exchange, String routingKey, T payload, String correlationId) {
        MDC.put("correlationId", correlationId);
        logger.info("Publishing data to exchange: {}, routingKey: {}, payload: {}", exchange, routingKey, payload);
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .headers(Map.of("X-Correlation-ID", correlationId))
                    .build();

            OutboundMessage msg = new OutboundMessage(exchange, routingKey, props, body);


            return sender.sendWithPublishConfirms(Mono.just(msg))
                    .next()
                    .doOnNext(confirm -> {
                        MDC.put("correlationId", correlationId);
                        if (confirm.isAck()) {
                            logger.info("✅ Broker ACK for data send to exchange: {} with routing key : {}", exchange, routingKey);
                        } else {
                            logger.info("❌ Broker NACK for data send to exchange: {} with routing key : {} and returned=: {}", exchange, routingKey, confirm.isReturned());
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

}
