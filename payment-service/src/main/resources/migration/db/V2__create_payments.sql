-- =========================
-- V2: Payments Table
-- =========================
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    payment_provider VARCHAR(30) NOT NULL, -- PAYSTACK, FLUTTERWAVE, WALLET
    reference VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    narration VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_reference UNIQUE (reference),
    CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key)
);

-- =========================
-- Indexes for performance
-- =========================
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status ON payments(status);
