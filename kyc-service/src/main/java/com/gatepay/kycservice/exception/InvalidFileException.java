package com.gatepay.kycservice.exception;

public class InvalidFileException extends KycServiceException {


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
