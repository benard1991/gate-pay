package com.gatepay.walletservice.controller;

import com.gatepay.walletservice.dto.ApiResponse;
import com.gatepay.walletservice.dto.WalletResponse;
import com.gatepay.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Service-to-service only. The gateway routes /api/v1/** paths, so /internal/** can't be
// reached from outside.
@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class InternalWalletController {

    private final WalletService walletService;

    // payment-service checks this before it sends a customer to Paystack/Flutterwave
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletByUserId(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully",
                walletService.getWalletByUserId(userId)));
    }
}
