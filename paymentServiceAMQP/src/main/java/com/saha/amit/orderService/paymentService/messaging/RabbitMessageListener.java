package com.saha.amit.orderService.paymentService.messaging;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.orderService.paymentService.dto.PaymentDto;
import com.saha.amit.orderService.paymentService.dto.PlaceOrderRequest;
import com.saha.amit.orderService.paymentService.dto.Status;
import com.saha.amit.orderService.paymentService.service.PaymentService;
import com.saha.amit.orderService.paymentService.util.PaymentUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.rabbitmq.client.Channel;

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
    @Value("${app.rabbit.exchange}")
    private String exchange;


    @RabbitListener(queues = "${app.rabbit.paymentQueue}", containerFactory = "manualAckContainerFactory")
    public void handleOrderCreated(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. Deserialize order
            PlaceOrderRequest placeOrderRequest = objectMapper.readValue(message.getBody(), PlaceOrderRequest.class);
            PaymentDto paymentDto = placeOrderRequest.getPayment();
            logger.info("Deserialized message picked from Rabbit MQ: {}, payment status: {}", placeOrderRequest, paymentDto.getPaymentStatus());


            if (null == paymentDto.getPaymentStatus()) {
                // 2. Save payment + outbox in one transaction
                // Making payment status IN_PROGRESS as it will be persisted in Outbox table and picked and published by OutboxPublisher
                String uuid = UUID.randomUUID().toString();
                paymentDto.setPaymentStatus(Status.IN_PROGRESS);
                paymentDto.setPaymentId(uuid);
                placeOrderRequest.setPayment(paymentDto);
                paymentService.processInitialOrder(paymentDto, placeOrderRequest, false)
                        .doOnNext(outboxEvent -> logger.info("💾 Saved payment and outbox event ={}", outboxEvent))
                        .doOnError(error -> logger.error("❌ Failed to persist initial order for orderId={}, error={}",
                                paymentDto.getPaymentId(), error.getMessage(), error))
                        .subscribe();
            } else if (paymentDto.getPaymentStatus() == Status.IN_PROGRESS) {
                paymentDto.setPaymentStatus(Status.COMPLETED);
                placeOrderRequest.getPayment().setPaymentStatus(Status.COMPLETED);
                paymentService.savePayment(paymentDto)
                        .doOnNext(payment -> logger.info("💾 Saved IN_PROGRESS payment ={}", payment))
                        .flatMap(payment -> paymentPublisher.publishEvent(
                                exchange,
                                "payment.success",
                                paymentUtil.toJson(placeOrderRequest),
                                placeOrderRequest.getOrderId()))
                        .doOnError(throwable -> logger.error("❌ Failed to publish payment.created event for orderId={}, error={}",
                                placeOrderRequest.getOrderId(), throwable.getMessage(), throwable))
                        .subscribe();
            }

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


}

