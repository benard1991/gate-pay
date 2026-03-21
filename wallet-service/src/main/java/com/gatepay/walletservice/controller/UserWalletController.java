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
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class UserWalletController {

    private final WalletService      walletService;
    private final TransactionService transactionService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @PathVariable Long userId) {
        log.info("Fetching wallet for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully",
                walletService.getWalletByUserId(userId)));
    }

    @PostMapping("/debit")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> debit(
            @Valid @RequestBody DebitWalletRequest request) {
        log.info("Debit request for userId: {} amount: {}", request.getUserId(), request.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Wallet debited successfully",
                        transactionService.debit(request)));
    }

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> getTransactions(
            @PathVariable Long userId,
            @ModelAttribute TransactionFilterRequest filter) {
        log.info("Fetching transactions for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully",
                transactionService.getTransactions(userId, filter)));
    }

    @GetMapping("/transactions/{reference}")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> getByReference(
            @PathVariable String reference) {
        log.info("Fetching transaction for reference: {}", reference);
        return ResponseEntity.ok(ApiResponse.success("Transaction retrieved successfully",
                transactionService.getByReference(reference)));
    }
}