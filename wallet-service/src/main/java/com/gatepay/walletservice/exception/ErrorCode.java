package com.gatepay.walletservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    USER_SERVICE_UNAVAILABLE("USR_001", "User service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    // General
    METHOD_NOT_ALLOWED("GEN_405", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_ERROR("GEN_500", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("GEN_400", "Validation failed", HttpStatus.BAD_REQUEST),

    // Wallet
    WALLET_NOT_FOUND("WAL_001", "Wallet not found", HttpStatus.NOT_FOUND),
    WALLET_ALREADY_EXISTS("WAL_002", "Wallet already exists for this user", HttpStatus.CONFLICT),
    WALLET_SUSPENDED("WAL_003", "Wallet is suspended", HttpStatus.FORBIDDEN),
    WALLET_CLOSED("WAL_004", "Wallet is closed", HttpStatus.FORBIDDEN),
    WALLET_NOT_ACTIVE("WAL_005", "Wallet is not active", HttpStatus.FORBIDDEN),
    WALLET_OPERATION_FAILED("WAL_006", "Wallet operation failed", HttpStatus.UNPROCESSABLE_ENTITY),

    // Transaction
    INSUFFICIENT_BALANCE("TXN_001", "Insufficient wallet balance", HttpStatus.UNPROCESSABLE_ENTITY),
    TRANSACTION_NOT_FOUND("TXN_002", "Transaction not found", HttpStatus.NOT_FOUND),
    TRANSACTION_ALREADY_REVERSED("TXN_003", "Transaction has already been reversed", HttpStatus.CONFLICT),
    TRANSACTION_NOT_REVERSIBLE("TXN_004", "Only successful transactions can be reversed", HttpStatus.UNPROCESSABLE_ENTITY),
    DUPLICATE_TRANSACTION("TXN_005", "Duplicate transaction detected", HttpStatus.CONFLICT),

    // Idempotency
    IDEMPOTENCY_SERIALIZE_ERROR("IDP_001", "Failed to serialize idempotency response", HttpStatus.INTERNAL_SERVER_ERROR),
    IDEMPOTENCY_DESERIALIZE_ERROR("IDP_002", "Failed to deserialize idempotency response", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public HttpStatus getStatus() { return status; }
}