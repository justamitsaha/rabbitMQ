package com.saha.amit.orderService.paymentService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import com.saha.amit.orderService.paymentService.domain.Payment;
import com.saha.amit.orderService.paymentService.repository.OutboxRepository;
import com.saha.amit.orderService.paymentService.repository.PaymentRepository;
import com.saha.amit.orderService.paymentService.messaging.PaymentPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public Mono<Payment> createPayment(String orderId) {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .status("INITIATED")
                .createdAt(Instant.now())
                .build();

        return paymentRepository.save(payment)
                .flatMap(saved -> {
                    try {
                        String payload = objectMapper.writeValueAsString(saved);
                        OutboxEvent event = OutboxEvent.builder()
                                .aggregateId(saved.getId())
                                .aggregateType("Payment")
                                .eventType("payment.created")
                                .payload(payload)
                                .createdAt(Instant.now())
                                .published(false)
                                .build();
                        return outboxRepository.save(event).thenReturn(saved);
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }
}

