-- Initialize Database
CREATE DATABASE IF NOT EXISTS order_schema;
USE order_schema;

-- Order Table (reactiveOrderService)
CREATE TABLE IF NOT EXISTS orders (
  order_id VARCHAR(36) PRIMARY KEY,
  customer_name VARCHAR(36),
  customer_id VARCHAR(50),
  order_status VARCHAR(50),
  created_at TIMESTAMP
);

-- Payments Table (paymentServiceAMQP)
CREATE TABLE IF NOT EXISTS payments (
  payment_id VARCHAR(36) NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  payment_status VARCHAR(20) NOT NULL, -- SUCCESS, FAILED, PENDING, IN_PROGRESS, COMPLETED
  amount DECIMAL(10,2) NOT NULL,
  payment_type VARCHAR(20) NOT NULL,   -- CARD, UPI, NETBANKING
  card_no VARCHAR(255) DEFAULT NULL,
  account_no VARCHAR(255) DEFAULT NULL,
  upi_id VARCHAR(255) DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (payment_id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Outbox Payments Table (paymentServiceAMQP)
CREATE TABLE IF NOT EXISTS outbox_payment (
    payment_id CHAR(36) PRIMARY KEY,
    aggregate_id CHAR(36),      -- payment_id or order_id
    aggregate_type VARCHAR(50), -- e.g., "Payment"
    event_type VARCHAR(50),     -- e.g., "payment.created"
    payload JSON,
    created_at TIMESTAMP,
    published BOOLEAN DEFAULT FALSE
);

-- Delivery Table (deliveryMessageService)
CREATE TABLE IF NOT EXISTS delivery (
  delivery_id VARCHAR(36) NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  delivery_status VARCHAR(20) NOT NULL,  -- PENDING, SHIPPED, DELIVERED
  address_line1 VARCHAR(255) NOT NULL,
  address_line2 VARCHAR(255) DEFAULT NULL,
  city VARCHAR(100) NOT NULL,
  state VARCHAR(100) NOT NULL,
  postal_code VARCHAR(20) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (delivery_id),
  KEY idx_order_delivery (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
