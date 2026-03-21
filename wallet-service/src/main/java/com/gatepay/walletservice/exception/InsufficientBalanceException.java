package com.gatepay.walletservice.exception;


public class InsufficientBalanceException extends WalletServiceException {
    public InsufficientBalanceException() {
        super(ErrorCode.INSUFFICIENT_BALANCE);
    }
    public InsufficientBalanceException(String customMessage) {
        super(ErrorCode.INSUFFICIENT_BALANCE, customMessage);
    }
}