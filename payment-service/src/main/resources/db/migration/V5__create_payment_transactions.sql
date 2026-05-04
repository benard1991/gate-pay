-- V5__create_payment_transactions.sql
-- Create table for payment transactions

CREATE TABLE payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference VARCHAR(100) NOT NULL UNIQUE,
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'NGN',
    customer_email VARCHAR(255),
    customer_name VARCHAR(255),
    phone_number VARCHAR(50),
    authorization_url VARCHAR(500),
    access_code VARCHAR(100),
    metadata JSON,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_transactions_reference ON payment_transactions(reference);
CREATE INDEX idx_payment_transactions_status ON payment_transactions(status);
CREATE INDEX idx_payment_transactions_customer_email ON payment_transactions(customer_email);
CREATE INDEX idx_payment_transactions_provider ON payment_transactions(provider);