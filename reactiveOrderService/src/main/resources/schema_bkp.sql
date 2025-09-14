use order_schema;

CREATE TABLE IF NOT EXISTS orders (
  id VARCHAR(36) PRIMARY KEY,
  customer_id VARCHAR(255),
  status VARCHAR(50),
  created_at TIMESTAMP
);

--CREATE TABLE IF NOT EXISTS outbox (
--  id BIGINT AUTO_INCREMENT PRIMARY KEY,
--  aggregate_type VARCHAR(100),
--  aggregate_id VARCHAR(36),
--  type VARCHAR(100),
--  payload CLOB,
--  status VARCHAR(20),
--  attempts INT DEFAULT 0,
--  created_at TIMESTAMP,
--  last_attempt_at TIMESTAMP
--);
