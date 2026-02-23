package com.gatepay.paymentservice.exception;

public class PaymentServiceException extends RuntimeException{
    private final ErrorCode errorCode;

    public PaymentServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PaymentServiceException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
    }

    public PaymentServiceException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }


    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
