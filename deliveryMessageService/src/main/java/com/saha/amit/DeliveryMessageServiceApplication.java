package com.saha.amit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DeliveryMessageServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryMessageServiceApplication.class);

    public static void main(String[] args) {
        String rabbitMq = "http://192.168.0.143:15672/";
        String swagger_UI = "http://localhost:8082/swagger-ui/index.html";
        SpringApplication.run(DeliveryMessageServiceApplication.class, args);
        logger.info("Swagger UI, {} ", swagger_UI);
        logger.info("Rabbit MQ, {} ", rabbitMq);
    }

}