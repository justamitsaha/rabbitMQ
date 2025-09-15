package com.saha.amit.orderService.paymentService.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.paymentService.domain.Order;
import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import com.saha.amit.orderService.paymentService.domain.Payment;
import com.saha.amit.orderService.paymentService.repository.CustomRepository;
import com.saha.amit.orderService.paymentService.repository.OutboxRepository;
import com.saha.amit.orderService.paymentService.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import com.rabbitmq.client.Channel;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RabbitMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMessageListener.class);

    private final ObjectMapper objectMapper;
    private final CustomRepository customRepository;

    @RabbitListener(queues = "${app.rabbit.orderQueue}", containerFactory = "manualAckContainerFactory")
    public void handleOrderCreated(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. Deserialize order
            Order order = objectMapper.readValue(message.getBody(), Order.class);
            logger.info("📥 Received order.created event for orderId={}", order.getId());

            // 2. Save payment + outbox in one transaction
            processOrder(order);

            // 3. Ack only after success
            channel.basicAck(deliveryTag, false);
            logger.info("✅ Acknowledged message for orderId={}", order.getId());

        } catch (Exception e) {
            logger.error("❌ Failed processing message: {}", e.getMessage(), e);
            // nack (false = single message, true = requeue)
            channel.basicNack(deliveryTag, false, false); // send to DLQ
        }
    }

    @Transactional
    public void processOrder(Order order) {
        // Persist payment
        Payment payment = Payment.builder()
                .id(order.getId())
                .orderId(order.getId())
                .status("INITIATED")
                .createdAt(Instant.now())
                .build();


        // Persist outbox
        OutboxEvent event = OutboxEvent.builder()
                .id(order.getId())
                .aggregateId(order.getId())
                .aggregateType("Payment")
                .eventType("payment.created")
                .payload(toJson(payment))
                .createdAt(Instant.now())
                .published(false)
                .build();

        customRepository.insertToPayment(payment)
                .then(customRepository.insertToOutbox(event))
                .subscribe();
        logger.info("💾 Saved payment and outbox event for orderId={}", order.getId());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize", e);
        }
    }
}

