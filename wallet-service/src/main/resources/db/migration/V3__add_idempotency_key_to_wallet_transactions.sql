-- Store the client's idempotency key on each transaction so the database,
-- not just Redis, guarantees a key is only applied once per wallet.
-- Existing rows stay NULL; MySQL allows multiple NULLs in a unique index.
ALTER TABLE wallet_transactions
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER reference,
    ADD UNIQUE KEY uk_wallet_transactions_wallet_idempotency (wallet_id, idempotency_key);
