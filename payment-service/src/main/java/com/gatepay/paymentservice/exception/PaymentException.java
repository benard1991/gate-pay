package com.gatepay.paymentservice.exception;

public class PaymentException extends PaymentServiceException {


    public PaymentException() {
        super(ErrorCode.KYC_WARNING);
    }

    public PaymentException(String customMessage) {
        super(ErrorCode.KYC_WARNING, customMessage);
    }
}

