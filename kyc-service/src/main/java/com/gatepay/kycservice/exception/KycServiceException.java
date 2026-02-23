package com.gatepay.kycservice.exception;

public class KycServiceException extends RuntimeException{
    private final ErrorCode errorCode;

    public KycServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public KycServiceException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
    }

    public KycServiceException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }


    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
