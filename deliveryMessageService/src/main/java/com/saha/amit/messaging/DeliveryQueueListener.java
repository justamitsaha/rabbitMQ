package com.saha.amit.messaging;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;

import java.nio.charset.StandardCharsets;


@Component
@RequiredArgsConstructor
public class DeliveryQueueListener {

    private final Mono<Receiver> receiverMono;
    private final DeliveryEventPublisher publisher;
    private static final Logger log = LoggerFactory.getLogger(DeliveryQueueListener.class);

    @Value("${app.mq.source-queue}")
    private String sourceQueue;

    @PostConstruct
    public void startListening() {
        receiverMono.flatMapMany(receiver -> receiver.consumeManualAck(sourceQueue))
                .flatMap(msg -> {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    log.info("📥 Received from {}: {}", sourceQueue, body);

                    // 👇 Insert your business logic here before publishing
                    return publisher.publish(msg.getBody())
                            .then(Mono.fromRunnable(() -> {
                                msg.ack(); // Ack only after successful publish
                                log.info("✅ Message acked: {}", body);
                            }))
                            .onErrorResume(ex -> {
                                msg.nack(false, false); // reject and send to DLQ
                                log.error("❌ Message nacked: {} due to error={}", body, ex.getMessage());
                                return Mono.empty();
                            });
                })
                .subscribe();
    }
}

