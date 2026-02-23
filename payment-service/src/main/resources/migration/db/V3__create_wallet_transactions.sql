-- =========================
-- V3: Wallet Transactions Table
-- =========================
CREATE TABLE wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    balance_before NUMERIC(18, 2) NOT NULL,
    balance_after NUMERIC(18, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL, -- CREDIT / DEBIT
    status VARCHAR(20) NOT NULL, -- SUCCESS / FAILED / REVERSED
    reference VARCHAR(100) NOT NULL,
    narration VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

-- =========================
-- Indexes for performance
-- =========================
CREATE INDEX idx_wallet_tx_wallet_id ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_tx_user_id ON wallet_transactions(user_id);
CREATE INDEX idx_wallet_tx_status ON wallet_transactions(status);
