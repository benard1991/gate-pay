-- Table to store KYC requests
CREATE TABLE kyc_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    nin VARCHAR(20) UNIQUE,
    bvn VARCHAR(20) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comments VARCHAR(500),
    verified_by BIGINT,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kyc_user_status (user_id, status)
);

-- Table to store uploaded documents
CREATE TABLE kyc_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kyc_request_id BIGINT NOT NULL,
    document_type VARCHAR(50),
    document_name VARCHAR(255),
    document_link VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_request FOREIGN KEY (kyc_request_id)
        REFERENCES kyc_requests (id)
        ON DELETE CASCADE
);