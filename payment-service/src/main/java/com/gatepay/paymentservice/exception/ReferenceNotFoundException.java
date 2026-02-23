package com.gatepay.paymentservice.exception;

public class ReferenceNotFoundException extends PaymentServiceException {

    public ReferenceNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }

    public ReferenceNotFoundException(String customMessage) {
        super(ErrorCode.USER_NOT_FOUND, customMessage);
    }
}
