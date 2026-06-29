package com.saha.amit.messaging;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    private final Mono<Sender> senderMono;
    private static final Logger log = LoggerFactory.getLogger(DeliveryEventPublisher.class);

    @Value("${app.mq.target-exchange}")
    private String targetExchange;

    @Value("${app.mq.routing-key}")
    private String routingKey;

    /**
     * Publishes raw JSON payload to domain.events exchange with routing key delivery.created
     * Uses publisher confirms for reliability.
     */
    public Mono<Void> publish(byte[] payload) {
        OutboundMessage message = new OutboundMessage(targetExchange, routingKey, payload);

        return senderMono.flatMapMany(sender ->
                        sender.sendWithPublishConfirms(Mono.just(message))
                )
                .flatMap(result -> {
                    if (result.isAck()) {
                        log.info("✅ Broker confirmed publish to exchange={}, routingKey={}",
                                targetExchange, routingKey);
                        return Mono.empty();
                    } else {
                        log.warn("⚠️ Broker NACKed publish to exchange={}, routingKey={}",
                                targetExchange, routingKey);
                        return Mono.error(new RuntimeException(
                                "Broker NACKed publish to " + targetExchange));
                    }
                })
                .doOnError(err ->
                        log.error("❌ Failed to publish to {}: {}", targetExchange, err.getMessage(), err)
                )
                .then(); // return Mono<Void>
    }
}
