package com.gatepay.paymentservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    METHOD_NOT_ALLOWED("kyc_405", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    USER_NOT_FOUND("USER_001", "User not found", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS("SER_002", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    USER_ALREADY_EXISTS("USER_409", "User with this email already exists", HttpStatus.CONFLICT),
    ACCOUNT_DISABLED("USER__003", "User account is disabled", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("USER_004", "Validation failed", HttpStatus.BAD_REQUEST),
    BAD_CREDENTIALS("USER_005", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("USER__999", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_FILE("KYC_007", "File size exceeds the maximum allowed size of 5 MB", HttpStatus.BAD_REQUEST),
    KYC_WARNING("KYC_094", "You already have a pending KYC request", HttpStatus.CONFLICT),
    INVALID_PASSWORD("AUTH_007", "Invalid password provided", HttpStatus.BAD_REQUEST),
    Email_REQUIRED("AUTH_080", "Email cannot be empty", HttpStatus.BAD_REQUEST),
    DUPLICATE_REQUEST("KYC_409", "Duplicate request detected. Please wait before retrying.", HttpStatus.CONFLICT),
    AUDIT_FAILURE("AUTH_020", "Email cannot be empty", HttpStatus.BAD_REQUEST),
    SERIALIZATION_ERROR("KYC_001", "Failed to serialize KYC data for audit trail", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_SERVICE_UNAVAILABLE("AUTH_006", "User-Service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // Payment-related error codes
    DUPLICATE_REFERENCE("PAY_001", "Transaction reference already exists", HttpStatus.CONFLICT),
    DUPLICATE_TRANSACTION("PAY_002", "Payment transaction already exists", HttpStatus.CONFLICT), // ✅ NEW
    PAYMENT_INITIALIZATION_FAILED("PAY_003", "Failed to initialize payment", HttpStatus.BAD_REQUEST),
    PAYMENT_VERIFICATION_FAILED("PAY_004", "Failed to verify payment", HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT("PAY_005", "Invalid payment amount", HttpStatus.BAD_REQUEST),
    CURRENCY_NOT_SUPPORTED("PAY_006", "Currency not supported", HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_FOUND("PAY_007", "Payment transaction not found", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_PROCESSED("PAY_008", "Payment has already been processed", HttpStatus.CONFLICT),
    KYC_NOT_VERIFIED("PAY_009", "KYC verification required to make payments", HttpStatus.FORBIDDEN),
    PROVIDER_ERROR("PAY_010", "Payment provider error", HttpStatus.BAD_GATEWAY),
    REFERENCE_ERROR("PAY_011", "Payment reference not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}