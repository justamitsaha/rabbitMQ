package com.saha.amit.orderService.paymentService.controller;

import com.saha.amit.orderService.paymentService.domain.Payment;
import com.saha.amit.orderService.paymentService.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{orderId}")
    public Mono<Payment> createPayment(@PathVariable String orderId) {
        return paymentService.createPayment(orderId);
    }
}
