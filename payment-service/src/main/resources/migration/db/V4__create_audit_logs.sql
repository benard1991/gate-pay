-- =========================
-- V4: Audit Logs Table
-- =========================
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,

    entity_name VARCHAR(50) NOT NULL,          -- e.g., 'Wallet', 'Payment', 'WalletTransaction'
    entity_id BIGINT NOT NULL,                 -- ID of the entity being modified
    action VARCHAR(50) NOT NULL,               -- e.g., 'CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE'

    performed_by BIGINT,                       -- user_id or system id
    ip_address VARCHAR(50),                     -- IPv4/IPv6
    user_agent VARCHAR(255),                    -- optional, browser/device info
     status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';

    old_data JSONB,                             -- previous state
    new_data JSONB,                             -- new state

    reference VARCHAR(100),                     -- optional reference (e.g., payment reference)

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================
-- Indexes for performance
-- =========================
CREATE INDEX idx_audit_entity ON audit_logs(entity_name, entity_id);
CREATE INDEX idx_audit_user ON audit_logs(performed_by);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_ip ON audit_logs(ip_address);
