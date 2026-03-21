CREATE TABLE wallets (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    user_id        BIGINT          NOT NULL,
    balance        DECIMAL(19, 4)  NOT NULL DEFAULT 0.0000,
    ledger_balance DECIMAL(19, 4)  NOT NULL DEFAULT 0.0000,
    currency       VARCHAR(3)      NOT NULL DEFAULT 'NGN',
    status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_wallets_user_id (user_id),
    CONSTRAINT chk_wallet_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT chk_balance CHECK (balance >= 0),
    CONSTRAINT chk_ledger_balance CHECK (ledger_balance >= 0)
);