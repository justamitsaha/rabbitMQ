package com.saha.amit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.domain.Delivery;
import com.saha.amit.dto.PlaceOrderRequest;
import com.saha.amit.dto.Status;
import com.saha.amit.repository.CustomDeliveryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryQueueListener {

    private final Mono<Receiver> receiverMono;
    private final DeliveryEventPublisher publisher;
    private final CustomDeliveryRepository customDeliveryRepository;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(DeliveryQueueListener.class);

    @Value("${app.mq.source-queue}")
    private String sourceQueue;

    @PostConstruct
    public void startListening() {
        receiverMono.flatMapMany(receiver -> receiver.consumeManualAck(sourceQueue))
                .flatMap(msg -> {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    log.info("📥 Received from {}: {}", sourceQueue, body);

                    try {
                        // 1. Deserialize PlaceOrderRequest
                        PlaceOrderRequest request = objectMapper.readValue(msg.getBody(), PlaceOrderRequest.class);
                        
                        // 2. Build Delivery entity
                        String deliveryId = UUID.randomUUID().toString();
                        String postalCode = request.getDelivery().getPostalCode();
                        Status deliveryStatus = Status.SUCCESS;
                        String targetRoutingKey = "delivery.success";
                        
                        if ("75034".equals(postalCode)) {
                            deliveryStatus = Status.FAILED;
                            targetRoutingKey = "delivery.failure";
                            log.warn("❌ Delivery failed for orderId={} because zip code is 75034", request.getOrderId());
                        }

                        Delivery delivery = Delivery.builder()
                                .deliveryId(deliveryId)
                                .orderId(request.getOrderId())
                                .deliveryStatus(deliveryStatus)
                                .addressLine1(request.getDelivery().getAddressLine1())
                                .addressLine2(request.getDelivery().getAddressLine2())
                                .city(request.getDelivery().getCity())
                                .state(request.getDelivery().getState())
                                .postalCode(postalCode)
                                .createdAt(Instant.now())
                                .build();

                        if (deliveryStatus == Status.SUCCESS) {
                            log.info("🚚 Simulating package shipping dispatch for orderId={} to {}...", 
                                     request.getOrderId(), delivery.getCity());
                        }

                        final String finalRoutingKey = targetRoutingKey;

                        // 3. Save Delivery and then publish event
                        return customDeliveryRepository.insert(delivery)
                                .then(publisher.publish(finalRoutingKey, msg.getBody()))
                                .then(Mono.fromRunnable(() -> {
                                    msg.ack(); // Ack only after successful DB save & publish
                                    log.info("✅ Message processed and acked: {}, routingKey={}", request.getOrderId(), finalRoutingKey);
                                }))
                                .onErrorResume(ex -> {
                                    msg.nack(false, false); // reject and send to DLQ
                                    log.error("❌ Message nacked for orderId={} due to error={}", 
                                              request.getOrderId(), ex.getMessage(), ex);
                                    return Mono.empty();
                                });
                                
                    } catch (Exception e) {
                        msg.nack(false, false); // reject and send to DLQ
                        log.error("❌ Failed to parse/process delivery event: {}", e.getMessage(), e);
                        return Mono.empty();
                    }
                })
                .subscribe();
    }
}

