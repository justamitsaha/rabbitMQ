package com.saha.amit.orderService.paymentService.service;

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
    private final RabbitTemplate rabbitTemplate;
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    @Value("${app.rabbit.paymentExchange}")
    private String exchange;

    @Scheduled(fixedDelay = 2000) // every 2s
    public void publishUnsentEvents() {
        logger.info("🔄 Checking for unsent outbox events...");
        outboxRepository.findByPublishedFalse()
                .flatMap(outboxEvent -> {
                    try {
                        CorrelationData correlationData = new CorrelationData(String.valueOf(outboxEvent.getAggregateId()));

                        rabbitTemplate.convertAndSend(exchange, outboxEvent.getEventType(), outboxEvent.getPayload(), correlationData);

                        logger.info("📤 Sent outboxEvent {} to RabbitMQ, awaiting confirm...", outboxEvent.getPayload());

                        // only mark as published after confirm callback
                        correlationData.getFuture().whenComplete((confirm, ex) -> {
                            if (ex != null) {
                                logger.error("❌ Publish failed for outboxEvent {}", outboxEvent.getAggregateId(), ex);
                                return;
                            }
                            if (confirm.isAck()) {
                                outboxEvent.setPublished(true);
                                outboxRepository.save(outboxEvent).subscribe();
                                logger.info("✅ Event {} published successfully", outboxEvent.getAggregateId());
                            } else {
                                logger.warn("⚠️ Event {} was NACKed by broker: {}", outboxEvent.getAggregateId(), confirm.getReason());
                            }
                        });

                        return Mono.empty(); // return empty since confirm callback does DB update
                    } catch (Exception e) {
                        logger.error("❌ Failed to publish outboxEvent {}", outboxEvent.getAggregateId(), e);
                        return Mono.empty();
                    }
                })
                .subscribe();
    }
}

