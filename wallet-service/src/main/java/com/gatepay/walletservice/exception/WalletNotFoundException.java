package com.gatepay.walletservice.exception;


public class WalletNotFoundException extends WalletServiceException {
    public WalletNotFoundException() {
        super(ErrorCode.WALLET_NOT_FOUND);
    }
    public WalletNotFoundException(String customMessage) {
        super(ErrorCode.WALLET_NOT_FOUND, customMessage);
    }
}