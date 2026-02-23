package com.gatepay.paymentservice.exception;

public class KycException extends  PaymentServiceException{


    public KycException() {
        super(ErrorCode.KYC_WARNING);
    }

    public KycException(String customMessage) {
        super(ErrorCode.KYC_WARNING, customMessage);
    }
}

