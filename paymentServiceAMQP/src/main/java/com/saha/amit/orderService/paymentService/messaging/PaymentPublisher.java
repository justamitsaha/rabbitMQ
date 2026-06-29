package com.saha.amit.orderService.paymentService.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class PaymentPublisher {

    private final Logger logger = LoggerFactory.getLogger(PaymentPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;



    public Mono<CorrelationData.Confirm> publishEvent(String exchange, String routingKey, Object payload, String correlationId) {
        try {
            CorrelationData correlationData = new CorrelationData(correlationId);

            logger.info("📦 Publishing data {} to RabbitMQ to exchange: {}, routing key: {}, with correlationId:{}",
                    payload, exchange, routingKey, correlationData.getId());

            rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData);

            return Mono.fromFuture(correlationData.getFuture())
                    .doOnError(ex -> logger.error("❌ Publish failed for outboxEvent {}", correlationId, ex))
                    .doOnNext(confirm -> {
                        if (confirm.isAck()) {
                            logger.info("✅ Event {} published successfully", correlationId);
                        } else {
                            logger.warn("⚠️ Event {} was NACKed by broker: {}", correlationId, confirm.getReason());
                        }
                    });

        } catch (Exception e) {
            logger.error("❌ Failed to publish outboxEvent {}", correlationId, e);
            return Mono.error(e);
        }
    }
}
