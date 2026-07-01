package com.saha.amit.orderService.dto;

import com.saha.amit.orderService.domain.Order;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailsDto {
    private Order order;
    private JsonNode payment;
    private JsonNode delivery;
}
