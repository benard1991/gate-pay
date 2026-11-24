package com.gatepay.walletservice.service;

import com.gatepay.walletservice.dto.*;


public interface TransactionService {

    WalletTransactionResponse credit(CreditWalletRequest request);

    WalletTransactionResponse debit(DebitWalletRequest request);

    WalletTransactionResponse reverse(String reference);

    WalletTransactionResponse getByReference(String reference);

    // Only finds the transaction if it belongs to the given user's wallet
    WalletTransactionResponse getByReferenceForUser(String reference, Long userId);

    PageResponse<WalletTransactionResponse> getTransactions(
            Long userId,
            TransactionFilterRequest filter
    );
    PageResponse<WalletTransactionResponse> getAllTransactions(AdminTransactionFilterRequest filter);

}