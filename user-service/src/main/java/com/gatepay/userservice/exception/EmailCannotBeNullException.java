package com.gatepay.userservice.exception;

public class EmailCannotBeNullException  extends UserServiceException{

    public EmailCannotBeNullException() {
        super(ErrorCode.EMAIL_REQUIRED);
    }

    public EmailCannotBeNullException(String customMessage) {
        super(ErrorCode.EMAIL_REQUIRED, customMessage);
    }
}
