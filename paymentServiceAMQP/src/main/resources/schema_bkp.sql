CREATE TABLE `payments` (
  `payment_id` VARCHAR(36) NOT NULL,
  `order_id` VARCHAR(36) NOT NULL,
  `payment_status` VARCHAR(20) NOT NULL, -- SUCCESS, FAILED, PENDING
  `amount` DECIMAL(10,2) NOT NULL,
  `payment_type` VARCHAR(20) NOT NULL,   -- CARD, UPI, NETBANKING
  `card_no` VARCHAR(25) DEFAULT NULL,
  `account_no` VARCHAR(30) DEFAULT NULL,
  `upi_id` VARCHAR(50) DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`payment_id`),
  KEY `idx_order_id` (`order_id`) -- keep index, but no FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
GINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE outbox_payment (
    payment_id CHAR(36) PRIMARY KEY,
    aggregate_id CHAR(36), -- paymentId
    aggregate_type VARCHAR(50), -- "Payment"
    event_type VARCHAR(50),     -- "payment.created"
    payload JSON,
    created_at TIMESTAMP,
    published BOOLEAN DEFAULT FALSE
);
