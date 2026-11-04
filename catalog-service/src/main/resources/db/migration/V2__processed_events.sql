CREATE TABLE processed_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    event_type VARCHAR(255),
    processed_at TIMESTAMP(6),
    CONSTRAINT uk_processed_event_id UNIQUE (event_id)
);
