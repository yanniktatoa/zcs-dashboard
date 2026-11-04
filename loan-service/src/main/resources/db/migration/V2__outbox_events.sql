CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(255),
    aggregate_type VARCHAR(255),
    aggregate_id BIGINT,
    payload TEXT,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6)
);
