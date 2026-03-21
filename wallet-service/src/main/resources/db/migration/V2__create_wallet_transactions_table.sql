CREATE TABLE wallet_transactions (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    wallet_id      BIGINT          NOT NULL,
    reference      VARCHAR(64)     NOT NULL,
    type           VARCHAR(20)     NOT NULL,
    source         VARCHAR(20)     NOT NULL,
    status         VARCHAR(20)     NOT NULL,
    amount         DECIMAL(19, 4)  NOT NULL,
    balance_before DECIMAL(19, 4)  NOT NULL,
    balance_after  DECIMAL(19, 4)  NOT NULL,
    description    VARCHAR(255),
    metadata       TEXT,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_transactions_reference (reference),

    -- Indexes
    INDEX idx_wallet_id  (wallet_id),
    INDEX idx_reference  (reference),
    INDEX idx_created_at (created_at),

    -- Constraints
    CONSTRAINT chk_transaction_type   CHECK (type   IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_transaction_source CHECK (source IN ('TOPUP', 'COMMISSION', 'TRANSFER', 'REVERSAL', 'REFUND', 'WITHDRAWAL')),
    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')),
    CONSTRAINT chk_amount             CHECK (amount > 0),

    -- Foreign key
    CONSTRAINT fk_wallet_transactions_wallet
        FOREIGN KEY (wallet_id)
        REFERENCES wallets (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);