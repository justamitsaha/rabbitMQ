package com.saha.amit.orderService.paymentService;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PaymentServiceAMQPApplication {
    private final static Logger logger = LoggerFactory.getLogger(PaymentServiceAMQPApplication.class);

    public static void main(String[] args) {
        String rabbitMq = "http://192.168.0.143:15672/";
        String swagger_UI = "http://localhost:8081/swagger-ui/index.html";
        SpringApplication.run(PaymentServiceAMQPApplication.class, args);
        logger.info("Swagger UI, {} ", swagger_UI);
        logger.info("Rabbit MQ, {} ", rabbitMq);
    }
}