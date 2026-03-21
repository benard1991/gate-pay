package com.gatepay.walletservice.exception;


public class TransactionNotFoundException extends WalletServiceException {
    public TransactionNotFoundException() {
        super(ErrorCode.TRANSACTION_NOT_FOUND);
    }
    public TransactionNotFoundException(String customMessage) {
        super(ErrorCode.TRANSACTION_NOT_FOUND, customMessage);
    }
}