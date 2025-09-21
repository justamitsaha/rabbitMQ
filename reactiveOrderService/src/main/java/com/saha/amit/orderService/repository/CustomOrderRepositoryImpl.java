package com.saha.amit.orderService.repository;

import com.saha.amit.orderService.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class CustomOrderRepositoryImpl implements CustomOrderRepository{
    Logger logger = LoggerFactory.getLogger(CustomOrderRepositoryImpl.class);

    private final DatabaseClient databaseClient;

    protected CustomOrderRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }


    public Mono<Order> insert(Order order) {
        logger.info("Inserting order: {}", order);
        String sql = """
                INSERT INTO orders
                (order_id,
                customer_id,
                customer_name,
                status,
                created_at)
                VALUES
                (:order_id,
                :customer_id,
                :customer_name,
                :status,
                :created_at);
                """;
        return databaseClient.sql(sql)
                .bind("order_id", order.getOrderId())
                .bind("customer_id", order.getCustomerId())
                .bind("customer_name", order.getCustomerName())
                .bind("status", order.getOrderStatus())
                .bind("created_at", order.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(order);
    }
}
