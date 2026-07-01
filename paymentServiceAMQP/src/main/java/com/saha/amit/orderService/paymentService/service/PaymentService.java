package com.saha.amit.orderService.paymentService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import com.saha.amit.orderService.paymentService.domain.Payment;
import com.saha.amit.orderService.paymentService.dto.PaymentDto;
import com.saha.amit.orderService.paymentService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.paymentService.dto.Status;
import com.saha.amit.orderService.paymentService.repository.CustomRepository;
import com.saha.amit.orderService.paymentService.repository.OutboxRepository;
import com.saha.amit.orderService.paymentService.repository.PaymentRepository;
import com.saha.amit.orderService.paymentService.util.PaymentUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final CustomRepository customRepository;
    private final PaymentUtil paymentUtil;

    /**
     * Creates a new Payment record in the database and logs a corresponding 
     * unpublished OutboxEvent for transaction safety.
     */
    public Mono<Payment> createPayment(String orderId) {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .createdAt(Instant.now())
                .build();

        return paymentRepository.save(payment)
                .flatMap(saved -> {
                    try {
                        String payload = objectMapper.writeValueAsString(saved);
                        OutboxEvent event = OutboxEvent.builder()
                                .aggregateId(saved.getPaymentId())
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


    /**
     * Saves the initial payment record and its corresponding outbox event in the database
     * inside a single transactional block to prevent dual-write inconsistencies.
     */
    @Transactional
    public Mono<OutboxEvent> processInitialOrder(PaymentDto paymentDto, PlaceOrderRequest placeOrderRequest, boolean processed) {
        // Persist payment
        Payment payment = Payment.builder()
                .paymentId(paymentDto.getPaymentId())
                .orderId(paymentDto.getOrderId())
                .paymentStatus(paymentDto.getPaymentStatus())
                .amount(paymentDto.getAmount())
                .paymentType(paymentDto.getPaymentType())
                .cardNo(paymentDto.getCardNo())
                .accountNo(paymentDto.getAccountNo())
                .upiId(paymentDto.getUpiId())
                .createdAt(Instant.now())
                .build();


        // Persist outbox
        OutboxEvent event = OutboxEvent.builder()
                .paymentId(payment.getPaymentId())
                .aggregateId(paymentDto.getOrderId())
                .aggregateType("Payment")
                .eventType("payment.created")
                .payload(paymentUtil.toJson(placeOrderRequest))
                .createdAt(Instant.now())
                .published(processed)
                .build();

        return customRepository.insertToPayment(payment)
                .then(customRepository.insertToOutbox(event))
                .doOnNext(outboxEvent -> logger.info("💾 Saved payment and outbox event ={}", outboxEvent));
    }


    /**
     * Saves or updates a Payment record directly in the database.
     */
    public Mono<Payment> savePayment(PaymentDto paymentDto) {
        // Persist payment
        Payment payment = Payment.builder()
                .paymentId(paymentDto.getPaymentId())
                .orderId(paymentDto.getOrderId())
                .paymentStatus(paymentDto.getPaymentStatus())
                .amount(paymentDto.getAmount())
                .paymentType(paymentDto.getPaymentType())
                .cardNo(paymentDto.getCardNo())
                .accountNo(paymentDto.getAccountNo())
                .upiId(paymentDto.getUpiId())
                .createdAt(Instant.now())
                .build();
        return paymentRepository.save(payment);
    }

    /**
     * Inserts a new Payment record in the database using raw SQL insert.
     */
    public Mono<Payment> insertPayment(PaymentDto paymentDto) {
        Payment payment = Payment.builder()
                .paymentId(paymentDto.getPaymentId())
                .orderId(paymentDto.getOrderId())
                .paymentStatus(paymentDto.getPaymentStatus())
                .amount(paymentDto.getAmount())
                .paymentType(paymentDto.getPaymentType())
                .cardNo(paymentDto.getCardNo())
                .accountNo(paymentDto.getAccountNo())
                .upiId(paymentDto.getUpiId())
                .createdAt(Instant.now())
                .build();
        return customRepository.insertToPayment(payment);
    }
}

