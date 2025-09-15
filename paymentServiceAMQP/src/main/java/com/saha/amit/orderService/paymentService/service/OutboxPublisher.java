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
        outboxRepository.findByPublishedFalse()
                .flatMap(event -> {
                    try {
                        CorrelationData correlationData = new CorrelationData(String.valueOf(event.getId()));

                        rabbitTemplate.convertAndSend(exchange, event.getEventType(), event.getPayload(), correlationData);

                        logger.info("📤 Sent event {} to RabbitMQ, awaiting confirm...", event.getId());

                        // only mark as published after confirm callback
                        correlationData.getFuture().whenComplete((confirm, ex) -> {
                            if (ex != null) {
                                logger.error("❌ Publish failed for event {}", event.getId(), ex);
                                return;
                            }
                            if (confirm.isAck()) {
                                event.setPublished(true);
                                outboxRepository.save(event).subscribe();
                                logger.info("✅ Event {} published successfully", event.getId());
                            } else {
                                logger.warn("⚠️ Event {} was NACKed by broker: {}", event.getId(), confirm.getReason());
                            }
                        });

                        return Mono.empty(); // return empty since confirm callback does DB update
                    } catch (Exception e) {
                        logger.error("❌ Failed to publish event {}", event.getId(), e);
                        return Mono.empty();
                    }
                })
                .subscribe();
    }
}

