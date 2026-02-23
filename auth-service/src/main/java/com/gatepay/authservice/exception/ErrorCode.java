package com.gatepay.authservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    USER_NOT_FOUND("AUTH_001", "User not found", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS("AUTH_002", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("AUTH_003", "User account is disabled", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("AUTH_004", "Validation failed", HttpStatus.BAD_REQUEST),
    BAD_CREDENTIALS("AUTH_005", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("AUTH_999", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_SERVICE_UNAVAILABLE("AUTH_006", "User-Service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_OTP("AUTH_007", "Invalid OTP", HttpStatus.BAD_REQUEST); // last constant ends with semicolon


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