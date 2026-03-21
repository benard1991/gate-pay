package com.gatepay.walletservice.service;


import com.gatepay.walletservice.model.WalletTransaction;

public interface WalletEventService {

    void publishCreditEvent(WalletTransaction transaction);

    void publishDebitEvent(WalletTransaction transaction);

    void publishReversalEvent(WalletTransaction transaction);

    void publishWalletCreatedEvent(Long userId, String currency);

    void publishWalletSuspendedEvent(Long userId);
}