package com.gatepay.paymentservice.exception;

public class InvalidFileException extends PaymentServiceException {


    public InvalidFileException() {
        super(ErrorCode.USER_NOT_FOUND);
    }

    public InvalidFileException(String customMessage) {
        super(ErrorCode.INVALID_FILE, customMessage);
    }

    // Custom message with cause
    public InvalidFileException(String customMessage, Throwable cause) {
        super(ErrorCode.INVALID_FILE, customMessage);
        initCause(cause);
    }
}
