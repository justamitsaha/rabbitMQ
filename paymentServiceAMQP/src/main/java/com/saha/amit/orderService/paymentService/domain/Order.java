package com.saha.amit.orderService.paymentService.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    private String id; // UUID string
    private String customerId;
    private OrderStatus status;
    private Instant createdAt;
}
