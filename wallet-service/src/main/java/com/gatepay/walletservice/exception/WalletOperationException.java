package com.gatepay.walletservice.exception;


public class WalletOperationException extends WalletServiceException {
    public WalletOperationException() {
        super(ErrorCode.WALLET_OPERATION_FAILED);
    }
    public WalletOperationException(String customMessage) {
        super(ErrorCode.WALLET_OPERATION_FAILED, customMessage);
    }
}