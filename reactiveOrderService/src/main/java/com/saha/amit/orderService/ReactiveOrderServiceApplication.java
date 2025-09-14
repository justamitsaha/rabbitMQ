package com.saha.amit.orderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveOrderServiceApplication {

    private final static Logger logger = LoggerFactory.getLogger(ReactiveOrderServiceApplication.class);

    public static void main(String[] args) {
        String rabbitMq = "http://192.168.0.143:15672/";
        String swagger_UI = "http://localhost:8080/swagger-ui/index.html";
        SpringApplication.run(ReactiveOrderServiceApplication.class, args);
        logger.info("Swagger UI, {} ", swagger_UI);
        logger.info("Rabbit MQ, {} ", rabbitMq);
    }
}
