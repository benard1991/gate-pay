package com.gatepay.paymentservice.exception;

public class DuplicateReferenceException extends PaymentServiceException{

    public DuplicateReferenceException() {
        super(ErrorCode.DUPLICATE_REFERENCE);
    }

    public DuplicateReferenceException(String customMessage) {
        super(ErrorCode.DUPLICATE_REFERENCE, customMessage);
    }
}


