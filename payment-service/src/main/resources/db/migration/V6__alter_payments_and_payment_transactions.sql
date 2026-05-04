-- V6: Alter payments and payment_transactions tables

-- =========================
-- 1. Alter payment_transactions table
-- =========================
ALTER TABLE payment_transactions
    ADD COLUMN user_id BIGINT NOT NULL,
    ADD COLUMN provider_transaction_id VARCHAR(100),
    ADD COLUMN gateway_response VARCHAR(500),
    ADD COLUMN paid_at TIMESTAMP;

CREATE INDEX idx_payment_transactions_user_id ON payment_transactions(user_id);

-- =========================
-- 2. Alter payments table
-- =========================
--ALTER TABLE payments
--    DROP COLUMN user_id;