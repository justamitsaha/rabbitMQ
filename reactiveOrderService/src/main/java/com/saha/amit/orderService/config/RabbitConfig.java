package com.saha.amit.orderService.config;

import com.rabbitmq.client.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.rabbitmq.*;

@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    @Value("${rabbitmq.host:localhost}")
    private String rabbitHost;
    @Value("${rabbitmq.port:5672}")
    private int rabbitPort;
    @Value("${rabbitmq.username:guest}")
    private String username;
    @Value("${rabbitmq.password:guest}")
    private String password;

    private ConnectionFactory createCf() {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setHost(rabbitHost);
        cf.setPort(rabbitPort);
        cf.setUsername(username);
        cf.setPassword(password);
        cf.useNio(); // optional for non-blocking
        return cf;
    }

    @Bean
    public Sender rabbitSender() {
        return RabbitFlux.createSender(new SenderOptions()
                .connectionFactory(createCf()));
    }

    @Bean
    public Receiver rabbitReceiver() {
        return RabbitFlux.createReceiver(new ReceiverOptions()
                .connectionFactory(createCf()));
    }
}

