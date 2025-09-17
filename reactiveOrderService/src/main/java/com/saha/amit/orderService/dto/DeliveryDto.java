package com.saha.amit.orderService.dto;

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
public class DeliveryDto {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String orderId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String deliveryId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String deliveryStatus;
    @Schema(description = "Location", example = "3654 Spencer street")
    private String addressLine1;
    @Schema(description = "Location", example = "Blue house, near park")
    private String addressLine2;
    @Schema(description = "City", example = "Torrance")
    private String city;
    @Schema(description = "CA", example = "California")
    private String state;
    @Schema(description = "Zip code", example = "90503")
    private String postalCode;
}
