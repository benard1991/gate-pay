package com.gatepay.paymentservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateTransactionException extends PaymentServiceException {

    public DuplicateTransactionException() {
        super(ErrorCode.DUPLICATE_TRANSACTION);
    }

    public DuplicateTransactionException(String customMessage) {
        super(ErrorCode.DUPLICATE_TRANSACTION, customMessage);
    }
}