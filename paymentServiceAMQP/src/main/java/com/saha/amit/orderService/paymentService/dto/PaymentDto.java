package com.saha.amit.orderService.paymentService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDto {
    private String orderId;
    private String paymentId;
    private Status paymentStatus; // SUCCESS, FAILED, PENDING
    @Schema(description = "Product price", example = "99.99")
    private Double amount;
    @Schema(description = "Payment type", example = "CARD")
    private PaymentType paymentType; // CARD/UPI/NETBANKING
    @Schema(description = "Card number", example = "1234-5678-9012-3456")
    private String cardNo;
    @Schema(description = "Account number", example = "1234567890")
    private String accountNo;
    @Schema (description = "UPI ID", example = "example@upi")
    private String upiId;
}
