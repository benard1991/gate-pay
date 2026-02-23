package com.gatepay.userservice.exception;


public class InvalidOtpException extends UserServiceException {

    public InvalidOtpException() {
        super(ErrorCode.INVALID_OTP);
    }

    public InvalidOtpException(String customMessage) {
        super(ErrorCode.USER_NOT_FOUND, customMessage);
    }
}
