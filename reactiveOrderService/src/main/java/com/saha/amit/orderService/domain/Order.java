package com.saha.amit.orderService.domain;

import com.saha.amit.orderService.dto.Status;
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
    private String orderId; // UUID string
    private String customerId;
    private String customerName;
    private Status orderStatus;
    private Instant createdAt;
}
