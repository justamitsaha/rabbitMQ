package com.saha.amit.orderService.paymentService.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("payments")
@ToString
public class Payment {
    @Id
    private String id;
    private String orderId;
    private String status; // INITIATED, COMPLETED, FAILED
    private Instant createdAt;
}
