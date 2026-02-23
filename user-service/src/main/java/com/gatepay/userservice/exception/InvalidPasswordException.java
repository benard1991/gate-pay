package com.gatepay.userservice.exception;

public class InvalidPasswordException extends UserServiceException{

    public InvalidPasswordException() {
        super(ErrorCode.USER_NOT_FOUND);
    }

    public InvalidPasswordException(String customMessage) {
        super(ErrorCode.USER_NOT_FOUND, customMessage);
    }
}
