ALTER TABLE kyc_audit_trail
    MODIFY COLUMN old_value LONGTEXT,
    MODIFY COLUMN new_value LONGTEXT;