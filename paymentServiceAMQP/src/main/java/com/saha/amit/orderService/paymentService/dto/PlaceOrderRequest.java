package com.saha.amit.orderService.paymentService.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
public class PlaceOrderRequest {
    private String orderId;
    @Schema(description = "Customer ID", example = "1")
    private String customerId;
    @Schema(description = "Customer Name", example = "Amit Saha")
    private String customerName;
    private Status orderStatus; // PLACED, APPROVED, REJECTED
    private Instant createdAt;
    private PaymentDto payment;
    private DeliveryDto delivery;
    // add items, amount etc. later
}
