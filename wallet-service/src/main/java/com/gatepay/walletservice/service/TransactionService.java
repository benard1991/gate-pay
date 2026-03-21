package com.gatepay.walletservice.service;

import com.gatepay.walletservice.dto.*;


public interface TransactionService {

    WalletTransactionResponse credit(CreditWalletRequest request);

    WalletTransactionResponse debit(DebitWalletRequest request);

    WalletTransactionResponse reverse(String reference);

    WalletTransactionResponse getByReference(String reference);

    PageResponse<WalletTransactionResponse> getTransactions(
            Long userId,
            TransactionFilterRequest filter
    );
}