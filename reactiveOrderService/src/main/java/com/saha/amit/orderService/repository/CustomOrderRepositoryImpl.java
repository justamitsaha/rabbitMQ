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
        return databaseClient.sql("INSERT INTO orders (id, customer_id, status, created_at) VALUES (:id, :customerId, :status, :createdAt)")
                .bind("id", order.getId())
                .bind("customerId", order.getCustomerId())
                .bind("status", order.getStatus())
                .bind("createdAt", order.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(order);
    }
}
