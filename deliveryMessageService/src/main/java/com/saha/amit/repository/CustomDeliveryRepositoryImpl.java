package com.saha.amit.repository;

import com.saha.amit.domain.Delivery;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class CustomDeliveryRepositoryImpl implements CustomDeliveryRepository {

    private static final Logger logger = LoggerFactory.getLogger(CustomDeliveryRepositoryImpl.class);

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Delivery> insert(Delivery delivery) {
        logger.info("Inserting delivery: {}", delivery);

        String sql = """
                INSERT INTO delivery
                (delivery_id,
                 order_id,
                 delivery_status,
                 address_line1,
                 address_line2,
                 city,
                 state,
                 postal_code,
                 created_at)
                VALUES
                (:delivery_id,
                 :order_id,
                 :delivery_status,
                 :address_line1,
                 :address_line2,
                 :city,
                 :state,
                 :postal_code,
                 :created_at);
                """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("delivery_id", delivery.getDeliveryId())
                .bind("order_id", delivery.getOrderId())
                .bind("delivery_status", delivery.getDeliveryStatus().name()) // Enum → String
                .bind("address_line1", delivery.getAddressLine1());

        spec = bindNullable(spec, "address_line2", delivery.getAddressLine2());

        return spec.bind("city", delivery.getCity())
                .bind("state", delivery.getState())
                .bind("postal_code", delivery.getPostalCode())
                .bind("created_at", delivery.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(delivery);
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        if (value != null) {
            return spec.bind(name, value);
        } else {
            return spec.bindNull(name, String.class);
        }
    }
}
