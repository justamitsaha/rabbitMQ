package com.saha.amit.config;

import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;

@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    @Value("${rabbitmq.host}")
    private String host;

    @Value("${rabbitmq.port}")
    private int port;

    @Value("${rabbitmq.username}")
    private String username;

    @Value("${rabbitmq.password}")
    private String password;

    private Sender sender;
    private Receiver receiver;

    @Bean(name = "rabbitConnectionFactory")
    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setAutomaticRecoveryEnabled(true);   // auto-reconnect
        factory.setNetworkRecoveryInterval(5000);    // retry every 5s
        return factory;
    }

    @Bean
    public Mono<Sender> sender(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                               ConnectionFactory rabbitConnectionFactory) {
        return Mono.fromCallable(() -> {
            sender = RabbitFlux.createSender(
                    new SenderOptions().connectionFactory(rabbitConnectionFactory)
            );
            return sender;
        }).cache();
    }

    @Bean
    public Mono<Receiver> receiver(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                                   ConnectionFactory rabbitConnectionFactory) {
        return Mono.fromCallable(() -> {
            receiver = RabbitFlux.createReceiver(
                    new ReceiverOptions().connectionFactory(rabbitConnectionFactory)
            );
            return receiver;
        }).cache();
    }

    @PreDestroy
    public void close() {
        if (sender != null) sender.close();
        if (receiver != null) receiver.close();
    }
}
