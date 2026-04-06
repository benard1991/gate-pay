package com.gatepay.paymentservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // General
    METHOD_NOT_ALLOWED("GEN_405", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),         // 405
    INTERNAL_ERROR("GEN_500", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),                         // 500
    VALIDATION_ERROR("GEN_422", "Validation failed", HttpStatus.UNPROCESSABLE_ENTITY),                                   // 422

    // User
    USER_NOT_FOUND("USR_001", "User not found", HttpStatus.NOT_FOUND),                                                   // 404
    USER_ALREADY_EXISTS("USR_002", "User with this email already exists", HttpStatus.CONFLICT),                          // 409
    ACCOUNT_DISABLED("USR_003", "User account is disabled", HttpStatus.FORBIDDEN),                                       // 403

    // Auth
    INVALID_CREDENTIALS("AUTH_001", "Invalid credentials", HttpStatus.UNAUTHORIZED),                                     // 401
    BAD_CREDENTIALS("AUTH_002", "Invalid username or password", HttpStatus.UNAUTHORIZED),                                 // 401
    INVALID_PASSWORD("AUTH_003", "Invalid password provided", HttpStatus.BAD_REQUEST),                                   // 400
    EMAIL_REQUIRED("AUTH_004", "Email cannot be empty", HttpStatus.BAD_REQUEST),                                         // 400
    AUDIT_FAILURE("AUTH_005", "Audit trail recording failed", HttpStatus.INTERNAL_SERVER_ERROR),                         // 500
    USER_SERVICE_UNAVAILABLE("AUTH_006", "User service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),       // 503

    // KYC
    INVALID_FILE("KYC_001", "File size exceeds the maximum allowed size of 5 MB", HttpStatus.BAD_REQUEST),               // 400
    SERIALIZATION_ERROR("KYC_002", "Failed to serialize KYC data for audit trail", HttpStatus.INTERNAL_SERVER_ERROR),    // 500
    KYC_WARNING("KYC_003", "You already have a pending KYC request", HttpStatus.CONFLICT),                               // 409
    DUPLICATE_REQUEST("KYC_004", "Duplicate request detected. Please wait before retrying.", HttpStatus.TOO_MANY_REQUESTS), // 429
    KYC_NOT_VERIFIED("KYC_005", "KYC verification required to make payments", HttpStatus.FORBIDDEN),                     // 403

    // Payment
    DUPLICATE_REFERENCE("PAY_001", "Transaction reference already exists", HttpStatus.CONFLICT),                         // 409
    DUPLICATE_TRANSACTION("PAY_002", "Payment transaction already exists", HttpStatus.CONFLICT),                         // 409
    PAYMENT_INITIALIZATION_FAILED("PAY_003", "Failed to initialize payment", HttpStatus.INTERNAL_SERVER_ERROR),          // 500
    PAYMENT_VERIFICATION_FAILED("PAY_004", "Failed to verify payment", HttpStatus.INTERNAL_SERVER_ERROR),                // 500
    INVALID_AMOUNT("PAY_005", "Invalid payment amount", HttpStatus.UNPROCESSABLE_ENTITY),                                // 422
    CURRENCY_NOT_SUPPORTED("PAY_006", "Currency not supported", HttpStatus.UNPROCESSABLE_ENTITY),                        // 422
    PAYMENT_NOT_FOUND("PAY_007", "Payment transaction not found", HttpStatus.NOT_FOUND),                                 // 404
    PAYMENT_ALREADY_PROCESSED("PAY_008", "Payment has already been processed", HttpStatus.CONFLICT),                     // 409
    PROVIDER_ERROR("PAY_009", "Payment provider error", HttpStatus.BAD_GATEWAY),                                         // 502
    REFERENCE_ERROR("PAY_010", "Payment reference not found", HttpStatus.NOT_FOUND),                                     // 404
    PAYMENT_PROCESSING("PAY_011", "Payment is currently being processed", HttpStatus.ACCEPTED);                          // 202
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