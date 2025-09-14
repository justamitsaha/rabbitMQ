package com.saha.amit.orderService.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("orders")
public class Order {
    @Id
    private String id; // UUID string
    private String customerId;
    private OrderStatus status;
    private Instant createdAt;
}
