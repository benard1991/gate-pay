package com.gatepay.userservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    METHOD_NOT_ALLOWED("USER_405", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    USER_NOT_FOUND("USER_001", "User not found", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS("SER_002", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    USER_ALREADY_EXISTS("USER_409", "User with this email already exists", HttpStatus.CONFLICT),
    ACCOUNT_DISABLED("USER__003", "User account is disabled", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("USER_004", "Validation failed", HttpStatus.BAD_REQUEST),
    BAD_CREDENTIALS("USER_005", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("USER__999", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_OTP("USER_007", "Invalid OTP", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD("AUTH_007", "Invalid password provided", HttpStatus.BAD_REQUEST),
    Email_REQUIRED("AUTH_080", "Email cannot be empty", HttpStatus.BAD_REQUEST),
    USER_SERVICE_UNAVAILABLE("AUTH_006", "User-Service is currently unavailable",HttpStatus.SERVICE_UNAVAILABLE );

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