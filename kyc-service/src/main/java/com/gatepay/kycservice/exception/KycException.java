package com.gatepay.kycservice.exception;

public class KycException  extends  KycServiceException{


    public KycException() {
        super(ErrorCode.KYC_WARNING);
    }

    public KycException(String customMessage) {
        super(ErrorCode.KYC_WARNING, customMessage);
    }
}

