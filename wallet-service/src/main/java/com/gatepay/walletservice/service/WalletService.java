package com.gatepay.walletservice.service;

import com.gatepay.walletservice.dto.CreateWalletRequest;
import com.gatepay.walletservice.dto.WalletResponse;

public interface WalletService {

    WalletResponse createWallet(CreateWalletRequest request);

    WalletResponse getWalletByUserId(Long userId);

    WalletResponse getWalletById(Long walletId);

    WalletResponse suspendWallet(Long userId);

    WalletResponse closeWallet(Long userId);

    WalletResponse reactivateWallet(Long userId);

    boolean walletExists(Long userId);
}