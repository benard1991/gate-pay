package com.gatepay.paymentservice.exception;

public class PaymentException extends PaymentServiceException {

    public PaymentException() {
        super(ErrorCode.PAYMENT_INITIALIZATION_FAILED);
    }

    public PaymentException(String customMessage) {
        super(ErrorCode.PAYMENT_INITIALIZATION_FAILED, customMessage);
    }

    public PaymentException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
