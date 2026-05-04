CREATE TABLE kyc_audit_trail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kyc_id BIGINT,
    performed_by BIGINT,
    performed_by_name VARCHAR(255),
    action VARCHAR(255),
    old_value LONGTEXT,
    new_value LONGTEXT,
    ip_address VARCHAR(45),
    performed_at DATETIME,
    CONSTRAINT fk_kyc_audit_kyc FOREIGN KEY (kyc_id)
        REFERENCES kyc_requests (id)
        ON DELETE CASCADE
);