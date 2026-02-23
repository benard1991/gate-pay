package com.gatepay.authservice.exception;

public class AuthServiceException extends RuntimeException{
    private final ErrorCode errorCode;

    public AuthServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AuthServiceException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
