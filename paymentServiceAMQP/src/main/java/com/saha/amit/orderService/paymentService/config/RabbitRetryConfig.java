package com.saha.amit.orderService.paymentService.config;


import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitRetryConfig {

    @Bean
    public RetryTemplate rabbitRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);   // 1s
        backOffPolicy.setMultiplier(2.0);         // doubles each retry
        backOffPolicy.setMaxInterval(30000);      // max 30s
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Bean
    public RabbitTemplate rabbitTemplateWithRetry(RabbitTemplate rabbitTemplate,
                                                  RetryTemplate rabbitRetryTemplate) {
        rabbitTemplate.setRetryTemplate(rabbitRetryTemplate);
        return rabbitTemplate;
    }
}

