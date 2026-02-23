
-- Table to store KYC requests
CREATE TABLE kyc_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comments VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table to store uploaded documents
CREATE TABLE kyc_documents (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kyc_request_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_link VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_request FOREIGN KEY (kyc_request_id)
        REFERENCES kyc_requests (id)
        ON DELETE CASCADE
);
