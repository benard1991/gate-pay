package com.gatepay.kycservice.exception;

public class IdempotencyException  extends KycServiceException{

    public IdempotencyException() {
        super(ErrorCode.DUPLICATE_REQUEST);
    }

    public IdempotencyException(String customMessage) {
        super(ErrorCode.DUPLICATE_REQUEST, customMessage);
    }

    // Custom message with cause
    public IdempotencyException(String customMessage, Throwable cause) {
        super(ErrorCode.DUPLICATE_REQUEST, customMessage);
        initCause(cause);
    }
}
