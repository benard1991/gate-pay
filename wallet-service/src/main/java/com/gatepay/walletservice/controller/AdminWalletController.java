package com.gatepay.walletservice.controller;



import com.gatepay.walletservice.dto.*;
import com.gatepay.walletservice.service.TransactionService;
import com.gatepay.walletservice.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/wallets/admin")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService      walletService;
    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {
        log.info("Admin creating wallet for userId: {}", request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Wallet created successfully",
                        walletService.createWallet(request)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @PathVariable Long userId) {
        log.info("Admin fetching wallet for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully",
                walletService.getWalletByUserId(userId)));
    }

    @PostMapping("/credit")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> credit(
            @Valid @RequestBody CreditWalletRequest request) {
        log.info("Admin credit for userId: {} amount: {}", request.getUserId(), request.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Wallet credited successfully",
                        transactionService.credit(request)));
    }

    @PostMapping("/reverse/{reference}")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> reverse(
            @PathVariable String reference) {
        log.info("Admin reversal for reference: {}", reference);
        return ResponseEntity.ok(ApiResponse.success("Transaction reversed successfully",
                transactionService.reverse(reference)));
    }

    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<WalletResponse>> suspendWallet(
            @PathVariable Long userId) {
        log.info("Admin suspending wallet for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet suspended successfully",
                walletService.suspendWallet(userId)));
    }

    @PatchMapping("/{userId}/reactivate")
    public ResponseEntity<ApiResponse<WalletResponse>> reactivateWallet(
            @PathVariable Long userId) {
        log.info("Admin reactivating wallet for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet reactivated successfully",
                walletService.reactivateWallet(userId)));
    }

    @PatchMapping("/{userId}/close")
    public ResponseEntity<ApiResponse<WalletResponse>> closeWallet(
            @PathVariable Long userId) {
        log.info("Admin closing wallet for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet closed successfully",
                walletService.closeWallet(userId)));
    }

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> getTransactions(
            @PathVariable Long userId,
            @ModelAttribute TransactionFilterRequest filter) {
        log.info("Admin fetching transactions for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully",
                transactionService.getTransactions(userId, filter)));
    }
}