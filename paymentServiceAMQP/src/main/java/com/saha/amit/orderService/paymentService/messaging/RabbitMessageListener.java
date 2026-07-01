package com.saha.amit.orderService.paymentService.messaging;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.paymentService.dto.PaymentType;
import com.saha.amit.orderService.paymentService.dto.PaymentDto;
import com.saha.amit.orderService.paymentService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.paymentService.dto.Status;
import com.saha.amit.orderService.paymentService.service.PaymentService;
import com.saha.amit.orderService.paymentService.util.PaymentUtil;
import com.saha.amit.orderService.paymentService.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.rabbitmq.client.Channel;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RabbitMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMessageListener.class);

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final PaymentPublisher paymentPublisher;
    private final PaymentUtil paymentUtil;
    @Value("${app.rabbit.exchange:domain.events}")
    private String exchange;

    @Value("${app.security.crypto-key}")
    private String cryptoKey;


    /**
     * Entry-point listener for all events arriving on the `payment-service-queue`,
     * coordinating deserialization, processing, and manual broker ACKs.
     */
    @RabbitListener(queues = "${app.rabbit.paymentQueue:payment-service-queue}", containerFactory = "manualAckContainerFactory")
    public void handleOrderCreated(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String receivedRoutingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            // 1. Deserialize order
            PlaceOrderRequest placeOrderRequest = objectMapper.readValue(message.getBody(), PlaceOrderRequest.class);
            
            Mono<Void> processMono;
            if ("delivery.failure".equals(receivedRoutingKey)) {
                processMono = revertPayment(placeOrderRequest);
            } else {
                processMono = processPayment(placeOrderRequest);
            }

            processMono
                    .doOnSuccess(result -> {
                        // Ack only if the pipeline succeeded
                        try {
                            channel.basicAck(deliveryTag, false);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        logger.info("✅ Acknowledged message (routingKey={}) for orderId={}", receivedRoutingKey, placeOrderRequest.getOrderId());
                    })
                    .doOnError(err -> {
                        logger.error("❌ Failed processing message (routingKey={}) for orderId={}, error={}",
                                receivedRoutingKey, placeOrderRequest.getOrderId(), err.getMessage(), err);
                        try {
                            channel.basicNack(deliveryTag, false, false); // send to DLQ
                        } catch (IOException ioException) {
                            logger.error("❌ Failed to nack message", ioException);
                        }
                    })
                    .subscribe();

        } catch (Exception e) {
            logger.error("❌ Failed processing message: {}", e.getMessage(), e);
            // nack (false = single message, true = requeue)
            channel.basicNack(deliveryTag, false, false); // send to DLQ
        }
    }

    /**
     * Routes payment requests based on their lifecycle status (initializing new orders
     * vs processing active card charges).
     */
    private Mono<Void> processPayment(PlaceOrderRequest placeOrderRequest) {
        PaymentDto paymentDto = placeOrderRequest.getPayment();
        logger.info("Deserialized message picked from Rabbit MQ: {}, payment status: {}", placeOrderRequest, paymentDto.getPaymentStatus());

        if (null == paymentDto.getPaymentStatus()) {
            if (paymentDto.getPaymentType() == PaymentType.CASH_ON_DELIVERY) {
                logger.warn("❌ Payment failed immediately because payment method is CASH_ON_DELIVERY (COD) for orderId={}", placeOrderRequest.getOrderId());
                String uuid = UUID.randomUUID().toString();
                paymentDto.setPaymentStatus(Status.FAILED);
                paymentDto.setPaymentId(uuid);
                placeOrderRequest.setPayment(paymentDto);

                return paymentService.insertPayment(paymentDto)
                        .flatMap(saved -> paymentPublisher.publishEvent(
                                exchange,
                                "payment.failure",
                                paymentUtil.toJson(placeOrderRequest),
                                placeOrderRequest.getOrderId()))
                        .then();
            }

            // 2. Save payment + outbox in one transaction
            // Making payment status IN_PROGRESS as it will be persisted in Outbox table and picked and published by OutboxPublisher
            String uuid = UUID.randomUUID().toString();
            paymentDto.setPaymentStatus(Status.IN_PROGRESS);
            paymentDto.setPaymentId(uuid);
            placeOrderRequest.setPayment(paymentDto);
            return paymentService.processInitialOrder(paymentDto, placeOrderRequest, false)
                    .doOnError(error -> logger.error("❌ Failed to persist initial order for orderId={}, error={}",
                            paymentDto.getPaymentId(), error.getMessage(), error))
                    .then(); // convert to Mono<Void>
        } else if (paymentDto.getPaymentStatus() == Status.IN_PROGRESS) {
            // Decrypt credentials to simulate secure card/bank authorization
            if (paymentDto.getCardNo() != null) {
                String dec = CryptoUtil.decrypt(paymentDto.getCardNo(), cryptoKey);
                logger.info("💳 Simulating card charge. Decrypted card_no = {}", dec);
            }
            if (paymentDto.getAccountNo() != null) {
                String dec = CryptoUtil.decrypt(paymentDto.getAccountNo(), cryptoKey);
                logger.info("🏦 Simulating bank transfer. Decrypted account_no = {}", dec);
            }
            if (paymentDto.getUpiId() != null) {
                String dec = CryptoUtil.decrypt(paymentDto.getUpiId(), cryptoKey);
                logger.info("📱 Simulating UPI debit. Decrypted upi_id = {}", dec);
            }

            paymentDto.setPaymentStatus(Status.COMPLETED);
            placeOrderRequest.getPayment().setPaymentStatus(Status.COMPLETED);
            return paymentService.savePayment(paymentDto)
                    .doOnNext(payment -> logger.info("💾 Saved IN_PROGRESS payment ={}", payment))
                    .flatMap(payment -> paymentPublisher.publishEvent(
                            exchange,
                            "payment.success",
                            paymentUtil.toJson(placeOrderRequest),
                            placeOrderRequest.getOrderId()))
                    .doOnError(throwable -> logger.error("❌ Failed to publish payment.created event for orderId={}, error={}",
                            placeOrderRequest.getOrderId(), throwable.getMessage(), throwable))
                    .then(); // convert to Mono<Void>
        }

        return Mono.empty(); // no-op
    }

    /**
     * Compensates/reverts payment when delivery fails.
     */
    private Mono<Void> revertPayment(PlaceOrderRequest placeOrderRequest) {
        PaymentDto paymentDto = placeOrderRequest.getPayment();
        if (paymentDto == null) {
            logger.warn("⚠️ No payment details found in delivery.failure event, cannot revert");
            return Mono.empty();
        }

        logger.warn("🔄 Reverting payment for orderId={} due to delivery failure...", placeOrderRequest.getOrderId());
        paymentDto.setPaymentStatus(Status.FAILED);
        return paymentService.savePayment(paymentDto)
                .doOnNext(saved -> logger.info("💾 Updated payment status to FAILED in database: {}", saved))
                .then();
    }
}

