package com.saha.amit.repository;

import com.saha.amit.domain.Delivery;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, String> {
}
