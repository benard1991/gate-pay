package com.gatepay.userservice.exception;

public class UserAlreadyExistsException extends UserServiceException{
    public UserAlreadyExistsException() {
        super(ErrorCode.USER_ALREADY_EXISTS);
    }

    public UserAlreadyExistsException(String customMessage) {
        super(ErrorCode.USER_ALREADY_EXISTS, customMessage);
    }
}
