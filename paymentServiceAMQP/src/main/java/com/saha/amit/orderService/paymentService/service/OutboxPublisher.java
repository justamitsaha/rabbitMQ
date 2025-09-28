package com.saha.amit.orderService.paymentService.service;

import com.saha.amit.orderService.paymentService.messaging.PaymentPublisher;
import com.saha.amit.orderService.paymentService.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final PaymentPublisher paymentPublisher;
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    @Value("${app.rabbit.exchange}")
    private String exchange;


    @Scheduled(fixedDelay = 2000) // every 2s
    public void publishUnsentOutBoxEvents() {
        outboxRepository.findByPublishedFalse()
                .flatMap(outboxEvent ->
                        paymentPublisher.publishEvent(exchange, "payment.created", outboxEvent.getPayload(), outboxEvent.getAggregateId())
                                .flatMap(confirm -> {
                                    if (confirm.isAck()) {
                                        outboxEvent.setPublished(true);
                                        return outboxRepository.save(outboxEvent)
                                                .doOnSuccess(saved -> logger.info("✅Outbox Event {} published successfully", outboxEvent.getPayload()))
                                                .doOnError(throwable -> logger.error("❌ Failed to mark outbox event {} as published", outboxEvent, throwable));
                                    } else {
                                        logger.warn("⚠️ Event {} was NACKed by broker, will retry later", outboxEvent.getAggregateId());
                                        return Mono.empty();
                                    }
                                })
                )
                .onErrorContinue((ex, obj) ->
                        logger.error("❌ Failed to process outbox event {}", obj, ex)
                )
                .subscribe();
    }


}

