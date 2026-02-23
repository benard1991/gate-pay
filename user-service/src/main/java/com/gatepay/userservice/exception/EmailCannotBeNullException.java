package com.gatepay.userservice.exception;

public class EmailCannotBeNullException  extends UserServiceException{

    public EmailCannotBeNullException() {
        super(ErrorCode.Email_REQUIRED);
    }

    public EmailCannotBeNullException(String customMessage) {
        super(ErrorCode.Email_REQUIRED, customMessage);
    }
}
