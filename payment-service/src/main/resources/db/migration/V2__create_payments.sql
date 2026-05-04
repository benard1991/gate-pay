-- =========================
-- V2: Payments Table
-- =========================
CREATE TABLE payments (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT          NOT NULL,
    amount           NUMERIC(18, 2)  NOT NULL,
    payment_provider VARCHAR(30)     NOT NULL,
    reference        VARCHAR(100)    NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    narration        VARCHAR(255),
    wallet_id        BIGINT          NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_payment_reference UNIQUE (reference)
);

CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status  ON payments(status);