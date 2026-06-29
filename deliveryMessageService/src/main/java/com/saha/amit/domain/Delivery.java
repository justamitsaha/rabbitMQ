package com.saha.amit.domain;

import com.saha.amit.dto.Status;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("delivery")
public class Delivery {

    @Id
    @Column("delivery_id")
    private String deliveryId;

    @Column("order_id")
    private String orderId;

    @Column("delivery_status")
    private Status deliveryStatus;

    @Column("address_line1")
    private String addressLine1;

    @Column("address_line2")
    private String addressLine2;

    @Column("city")
    private String city;

    @Column("state")
    private String state;

    @Column("postal_code")
    private String postalCode;

    @Column("created_at")
    private Instant createdAt;
}
