package com.gatepay.userservice.exception;

public class UserServiceException extends RuntimeException{
    private final ErrorCode errorCode;

    public UserServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public UserServiceException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }


    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
