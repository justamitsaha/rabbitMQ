package com.saha.amit.controller;

import com.saha.amit.domain.Delivery;
import com.saha.amit.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@CrossOrigin
@RestController
@RequestMapping("/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryRepository deliveryRepository;

    @GetMapping("/order/{orderId}")
    public Mono<Delivery> getDeliveryByOrderId(@PathVariable String orderId) {
        return deliveryRepository.findByOrderId(orderId);
    }
}
