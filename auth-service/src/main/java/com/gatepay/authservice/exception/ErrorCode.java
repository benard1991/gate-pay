package com.gatepay.authservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // General
    METHOD_NOT_ALLOWED("GEN_405", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_ERROR("GEN_500", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("GEN_400", "Validation failed", HttpStatus.BAD_REQUEST),

    // User
    USER_NOT_FOUND("USR_001", "User not found", HttpStatus.NOT_FOUND),
    ACCOUNT_DISABLED("USR_003", "User account is disabled", HttpStatus.FORBIDDEN),

    // Auth
    INVALID_CREDENTIALS("AUTH_001", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS("AUTH_002", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_OTP("AUTH_003", "Invalid OTP", HttpStatus.BAD_REQUEST),
    USER_SERVICE_UNAVAILABLE("AUTH_004", "User service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE);

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