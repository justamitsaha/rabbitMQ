package com.saha.amit.orderService.paymentService.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        // Confirm callback: triggered when broker ACK/NACK
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                logger.info("✅ Message confirmed by broker, correlationId={}", correlationData != null ? correlationData.getId() : "null");
            } else {
                logger.error("❌ Message NACKed by broker: {}", cause);
            }
        });

        // Return callback: triggered when message cannot be routed to a queue
        rabbitTemplate.setReturnsCallback(returned -> {
            logger.error("❌ Message returned: {}", returned);
        });

        return rabbitTemplate;
    }
}

