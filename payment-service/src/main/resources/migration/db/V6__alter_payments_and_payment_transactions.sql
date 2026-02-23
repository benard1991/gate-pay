-- V5__alter_payments_and_payment_transactions.sql

-- =========================
-- 1. Alter payment_transactions table
-- =========================

-- Add user_id and wallet_id columns
ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL,
    ADD COLUMN IF NOT EXISTS wallet_id BIGINT NOT NULL;

-- Add optional provider transaction tracking columns
ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS provider_transaction_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS gateway_response VARCHAR(500),
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_payment_transactions_user_id ON payment_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_wallet_id ON payment_transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_reference ON payment_transactions(reference);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_status ON payment_transactions(status);

-- =========================
-- 2. Alter payments table
-- =========================

-- Drop user_id and wallet_id from payments table
ALTER TABLE payments
    DROP COLUMN IF EXISTS user_id,
    DROP COLUMN IF EXISTS wallet_id;

-- Ensure reference is unique
ALTER TABLE payments
    ADD CONSTRAINT IF NOT EXISTS uk_payments_reference UNIQUE(reference);

-- Add/update indexes
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

-- Set default for updated_at if not already
ALTER TABLE payments
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

-- Optional: idempotency_key uniqueness if not already
ALTER TABLE payments
    ADD CONSTRAINT IF NOT EXISTS uk_payments_idempotency UNIQUE(idempotency_key);
