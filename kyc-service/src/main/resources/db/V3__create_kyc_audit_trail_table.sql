CREATE TABLE kyc_audit_trail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kyc_id BIGINT NOT NULL,
    performed_by BIGINT NOT NULL,
    performed_by_name VARCHAR(100),   -- optional snapshot of user's name
    action VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(45),
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT FK_kyc_audit_kyc FOREIGN KEY (kyc_id) REFERENCES kyc_requests(id) ON DELETE CASCADE,
    CONSTRAINT FK_kyc_audit_user FOREIGN KEY (performed_by) REFERENCES users(id)
);
