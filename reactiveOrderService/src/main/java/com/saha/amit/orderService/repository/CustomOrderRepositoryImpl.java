package com.saha.amit.orderService.repository;

import com.saha.amit.orderService.domain.Order;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class CustomOrderRepositoryImpl implements CustomOrderRepository{

    private final DatabaseClient databaseClient;

    protected CustomOrderRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }


    public Mono<Order> insert(Order order) {
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
                .bind("createdAt", order.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(order);
    }
}
