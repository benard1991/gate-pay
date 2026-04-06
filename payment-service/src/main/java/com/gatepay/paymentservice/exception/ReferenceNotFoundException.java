package com.gatepay.paymentservice.exception;

public class ReferenceNotFoundException extends PaymentServiceException {

    public ReferenceNotFoundException() {
        super(ErrorCode.REFERENCE_ERROR);
    }

    public ReferenceNotFoundException(String customMessage) {
        super(ErrorCode.REFERENCE_ERROR, customMessage);
    }
}
