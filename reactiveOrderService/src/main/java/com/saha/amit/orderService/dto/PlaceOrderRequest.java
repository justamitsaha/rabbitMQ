package com.saha.amit.orderService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
public class PlaceOrderRequest {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String orderId;
    @Schema(description = "Customer ID", example = "1")
    private String customerId;
    @Schema(description = "Customer Name", example = "Amit Saha")
    private String customerName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String orderStatus; // PLACED, APPROVED, REJECTED
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;
    private PaymentDto payment;
    private DeliveryDto delivery;
    // add items, amount etc. later
}
