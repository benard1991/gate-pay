package com.gatepay.kycservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // General
    METHOD_NOT_ALLOWED("GEN_405", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),        // 405
    INTERNAL_ERROR("GEN_500", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),                        // 500
    VALIDATION_ERROR("GEN_422", "Validation failed", HttpStatus.UNPROCESSABLE_ENTITY),                                  // 422

    // User
    USER_NOT_FOUND("USR_404", "User not found", HttpStatus.NOT_FOUND),                                                  // 404
    USER_ALREADY_EXISTS("USR_409", "User with this email already exists", HttpStatus.CONFLICT),                         // 409
    ACCOUNT_DISABLED("USR_403", "User account is disabled", HttpStatus.FORBIDDEN),                                      // 403

    // Auth
    INVALID_CREDENTIALS("AUTH_401", "Invalid credentials", HttpStatus.UNAUTHORIZED),                                    // 401
    BAD_CREDENTIALS("AUTH_401", "Invalid username or password", HttpStatus.UNAUTHORIZED),                               // 401
    INVALID_PASSWORD("AUTH_400", "Invalid password provided", HttpStatus.BAD_REQUEST),                                  // 400
    EMAIL_REQUIRED("AUTH_400", "Email cannot be empty", HttpStatus.BAD_REQUEST),                                        // 400
    AUDIT_FAILURE("AUTH_500", "Audit trail recording failed", HttpStatus.INTERNAL_SERVER_ERROR),                        // 500
    USER_SERVICE_UNAVAILABLE("AUTH_503", "User service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),      // 503

    // KYC
    INVALID_FILE("KYC_400", "File size exceeds the maximum allowed size of 5 MB", HttpStatus.BAD_REQUEST),              // 400
    SERIALIZATION_ERROR("KYC_500", "Failed to serialize KYC data for audit trail", HttpStatus.INTERNAL_SERVER_ERROR),   // 500
    KYC_WARNING("KYC_409", "You already have a pending KYC request", HttpStatus.CONFLICT),                              // 409
    DUPLICATE_REQUEST("KYC_429", "Duplicate request detected. Please wait before retrying.", HttpStatus.TOO_MANY_REQUESTS); // 429


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