-- =========================
-- V4: Audit Logs Table
-- =========================
CREATE TABLE audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_name  VARCHAR(50)  NOT NULL,
    entity_id    BIGINT       NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    performed_by VARCHAR(100),
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(255),
    status       VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    narration    VARCHAR(255),
    old_data     JSON,
    new_data     JSON,
    reference    VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_name, entity_id);
CREATE INDEX idx_audit_user   ON audit_logs(performed_by);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_ip     ON audit_logs(ip_address);