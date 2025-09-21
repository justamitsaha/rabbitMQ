package com.saha.amit.orderService.paymentService.domain;

import com.saha.amit.orderService.paymentService.dto.PaymentType;
import com.saha.amit.orderService.paymentService.dto.Status;
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
    private String paymentId;
    private String orderId;
    private Status paymentStatus; // SUCCESS, FAILED, PENDING
    private Double amount;
    private PaymentType paymentType; // CARD, UPI, NETBANKING
    private String cardNo;
    private String accountNo;
    private String upiId;
    private Instant createdAt;

}
