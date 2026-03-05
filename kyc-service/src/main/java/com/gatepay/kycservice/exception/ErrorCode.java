package com.gatepay.kycservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // General
    METHOD_NOT_ALLOWED("GEN_405", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_ERROR("GEN_500", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("GEN_400", "Validation failed", HttpStatus.BAD_REQUEST),

    // User
    USER_NOT_FOUND("USR_001", "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("USR_409", "User with this email already exists", HttpStatus.CONFLICT),
    ACCOUNT_DISABLED("USR_003", "User account is disabled", HttpStatus.FORBIDDEN),

    // Auth
    INVALID_CREDENTIALS("AUTH_001", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS("AUTH_002", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_PASSWORD("AUTH_003", "Invalid password provided", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED("AUTH_004", "Email cannot be empty", HttpStatus.BAD_REQUEST),
    AUDIT_FAILURE("AUTH_005", "Audit trail recording failed", HttpStatus.BAD_REQUEST),
    USER_SERVICE_UNAVAILABLE("AUTH_006", "User service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // KYC
    INVALID_FILE("KYC_001", "File size exceeds the maximum allowed size of 5 MB", HttpStatus.BAD_REQUEST),
    SERIALIZATION_ERROR("KYC_002", "Failed to serialize KYC data for audit trail", HttpStatus.INTERNAL_SERVER_ERROR),
    KYC_WARNING("KYC_003", "You already have a pending KYC request", HttpStatus.CONFLICT),
    DUPLICATE_REQUEST("KYC_004", "Duplicate request detected. Please wait before retrying.", HttpStatus.CONFLICT);

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