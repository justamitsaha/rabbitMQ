CREATE TABLE `delivery` (
  `delivery_id` VARCHAR(36) NOT NULL,
  `order_id` VARCHAR(36) NOT NULL,
  `delivery_status` VARCHAR(20) NOT NULL,  -- PENDING, SHIPPED, DELIVERED, etc.
  `address_line1` VARCHAR(255) NOT NULL,
  `address_line2` VARCHAR(255) DEFAULT NULL,
  `city` VARCHAR(100) NOT NULL,
  `state` VARCHAR(100) NOT NULL,
  `postal_code` VARCHAR(20) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`delivery_id`),
  KEY `idx_order_delivery` (`order_id`) -- just an index, no FK
)