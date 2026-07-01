package com.saha.amit.orderService.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.rabbitmq.Receiver;

import java.nio.charset.StandardCharsets;

@CrossOrigin
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private final Receiver receiver;
    private final ObjectMapper objectMapper;
    
    // Multicast sink to broadcast events to all active SSE subscribers
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
    private Disposable subscription;

    /**
     * Subscribes to the RabbitMQ notification-service-queue reactively on startup 
     * and broadcasts any received event to the multicast sink.
     */
    @PostConstruct
    public void startListening() {
        log.info("🚀 Starting Notification SSE Controller. Listening on 'notification-service-queue'...");
        subscription = receiver.consumeAutoAck("notification-service-queue")
                .doOnNext(delivery -> {
                    try {
                        String eventType = delivery.getProperties().getType();
                        if (eventType == null) {
                            eventType = delivery.getEnvelope().getRoutingKey();
                        }
                        String payloadString = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        JsonNode payload = objectMapper.readTree(payloadString);
                        
                        String message = String.format("{\"event\":\"%s\", \"payload\":%s}", eventType, payload.toString());
                        log.info("🔔 Captured notification event: {}", message);
                        sink.tryEmitNext(message);
                    } catch (Exception e) {
                        log.error("❌ Error parsing notification event", e);
                    }
                })
                .subscribe();
    }

    /**
     * Exposes the SSE endpoint streaming all captured RabbitMQ event logs in real time.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamNotifications() {
        return sink.asFlux()
                .map(event -> ServerSentEvent.<String>builder()
                        .data(event)
                        .build());
    }

    /**
     * Closes the active RabbitMQ listener subscription when the controller is destroyed.
     */
    @PreDestroy
    public void stopListening() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("🛑 Stopped Notification SSE Controller.");
        }
    }
}
