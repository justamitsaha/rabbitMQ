package com.saha.amit.repository;

import com.saha.amit.domain.Delivery;
import reactor.core.publisher.Mono;

public interface CustomDeliveryRepository {
    Mono<Delivery> insert(Delivery delivery);
}

