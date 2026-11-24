-- V7__create_outbox_events.sql
-- Events waiting to be sent to RabbitMQ (see OutboxPublisher)

CREATE TABLE outbox_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    exchange_name   VARCHAR(100) NOT NULL,
    routing_key     VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    next_attempt_at DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    sent_at         DATETIME(6),

    -- only one PAYMENT_SUCCESS per payment reference
    CONSTRAINT uk_outbox_aggregate_event UNIQUE (aggregate_id, event_type)
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox_events (status, next_attempt_at, id);
