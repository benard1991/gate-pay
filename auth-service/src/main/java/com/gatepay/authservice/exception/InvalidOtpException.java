package com.gatepay.authservice.exception;

public class InvalidOtpException  extends AuthServiceException {
    public InvalidOtpException() {
        super(ErrorCode.INVALID_OTP);
    }

    public InvalidOtpException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
