package com.saha.amit.orderService.paymentService.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.paymentService.domain.OutboxEvent;
import com.saha.amit.orderService.paymentService.domain.Payment;
import com.saha.amit.orderService.paymentService.dto.PaymentDto;
import com.saha.amit.orderService.paymentService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.paymentService.dto.Status;
import com.saha.amit.orderService.paymentService.repository.CustomRepository;
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
            PlaceOrderRequest placeOrderRequest = objectMapper.readValue(message.getBody(), PlaceOrderRequest.class);
            logger.info("Deserialized PlaceOrderRequest: {}", placeOrderRequest);
            PaymentDto paymentDto = placeOrderRequest.getPayment();

            // 2. Save payment + outbox in one transaction
            processOrder(paymentDto);

            // 3. Ack only after success this tells RabbitMQ we have processed this message
            //If we comment this line message will be read again after consumer restart
            // We will need to restart the consumer application to see the message being processed again
            channel.basicAck(deliveryTag, false);
            logger.info("✅ Acknowledged message for orderId={}", placeOrderRequest.getOrderId());

        } catch (Exception e) {
            logger.error("❌ Failed processing message: {}", e.getMessage(), e);
            // nack (false = single message, true = requeue)
            channel.basicNack(deliveryTag, false, false); // send to DLQ
        }
    }


    @RabbitListener(queues = "${app.rabbit.paymentQueue}", containerFactory = "manualAckContainerFactory")
    public void handlePaymentCreated(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. Deserialize order
            Payment payment = objectMapper.readValue(message.getBody(), Payment.class);
            logger.info("Deserialized Payment: {}", payment);

            // 2. Process payment logic here (e.g., call payment gateway)
            // Simulate processing delay
            Thread.sleep(1000);
            logger.info("Processed payment for paymentId={}", payment.getPaymentId());

            // 3. Ack only after success this tells RabbitMQ we have processed this message
            //If we comment this line message will be read again after consumer restart
            // We will need to restart the consumer application to see the message being processed again
            channel.basicAck(deliveryTag, false);
            logger.info("✅ Acknowledged message for paymentId={}", payment.getPaymentId());

        } catch (Exception e) {
            logger.error("❌ Failed processing message: {}", e.getMessage(), e);
            // nack (false = single message, true = requeue)
            channel.basicNack(deliveryTag, false, false); // send to DLQ
        }
    }

    @Transactional
    public void processOrder(PaymentDto paymentDto) {
        // Persist payment
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID().toString())
                .orderId(paymentDto.getOrderId())
                .paymentStatus(Status.PENDING)
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
                .payload(toJson(payment))
                .createdAt(Instant.now())
                .published(false)
                .build();

        customRepository.insertToPayment(payment)
                .then(customRepository.insertToOutbox(event))
                .subscribe();
        logger.info("💾 Saved payment and outbox event ={}", paymentDto);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize", e);
        }
    }
}

