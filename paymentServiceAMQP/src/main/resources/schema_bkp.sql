CREATE TABLE payments (
    id CHAR(36) PRIMARY KEY,
    order_id CHAR(36),
    status VARCHAR(50),
    created_at TIMESTAMP
);

CREATE TABLE outbox (
    id CHAR(36) PRIMARY KEY,
    aggregate_id CHAR(36), -- paymentId
    aggregate_type VARCHAR(50), -- "Payment"
    event_type VARCHAR(50),     -- "payment.created"
    payload JSON,
    created_at TIMESTAMP,
    published BOOLEAN DEFAULT FALSE
);
