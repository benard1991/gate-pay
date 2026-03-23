package com.gatepay.walletservice.service;


import com.fasterxml.jackson.databind.ObjectMapper;

import com.gatepay.walletservice.dto.*;
import com.gatepay.walletservice.enums.TransactionSource;
import com.gatepay.walletservice.enums.TransactionStatus;
import com.gatepay.walletservice.enums.TransactionType;
import com.gatepay.walletservice.enums.WalletStatus;
import com.gatepay.walletservice.exception.ErrorCode;
import com.gatepay.walletservice.exception.InsufficientBalanceException;
import com.gatepay.walletservice.exception.TransactionNotFoundException;
import com.gatepay.walletservice.exception.WalletNotFoundException;
import com.gatepay.walletservice.exception.WalletServiceException;
import com.gatepay.walletservice.model.*;
import com.gatepay.walletservice.repository.WalletRepository;
import com.gatepay.walletservice.repository.WalletTransactionRepository;
import com.gatepay.walletservice.specification.TransactionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final WalletRepository            walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final IdempotencyService          idempotencyService;
    private final WalletEventService          walletEventService;
    private final ObjectMapper                objectMapper;

    // ─────────────────────────────────────────
    // CREDIT
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public WalletTransactionResponse credit(CreditWalletRequest request) {

        // 1. Idempotency check
        String userId = String.valueOf(request.getUserId());
        var cached = idempotencyService.getProcessedResponse(
                userId, request.getIdempotencyKey()
        );
        if (cached.isPresent()) {
            log.info("Duplicate credit request detected for userId: {}", userId);
            return deserialize(cached.get());
        }

        // 2. Fetch wallet with optimistic lock
        Wallet wallet = walletRepository
                .findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + request.getUserId()
                ));

        // 3. Validate wallet status
        validateWalletActive(wallet);

        // 4. Record balances before update
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter  = balanceBefore.add(request.getAmount());

        // 5. Update wallet balance
        wallet.setBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);

        // 6. Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .reference(generateReference())
                .type(TransactionType.CREDIT)
                .source(request.getSource())
                .status(TransactionStatus.SUCCESS)
                .amount(request.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(request.getDescription())
                .metadata(request.getMetadata())
                .build();

        WalletTransaction saved = transactionRepository.save(transaction);
        log.info("Credit successful — userId: {} amount: {} ref: {}",
                userId, request.getAmount(), saved.getReference());

        // 7. Build response
        WalletTransactionResponse response = mapToResponse(saved);

        // 8. Store in Redis for idempotency
        idempotencyService.storeResponse(userId, request.getIdempotencyKey(),
                serialize(response));

        // 9. Publish event
        walletEventService.publishCreditEvent(saved);

        return response;
    }

    // ─────────────────────────────────────────
    // DEBIT
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public WalletTransactionResponse debit(DebitWalletRequest request) {

        // 1. Idempotency check
        String userId = String.valueOf(request.getUserId());
        var cached = idempotencyService.getProcessedResponse(
                userId, request.getIdempotencyKey()
        );
        if (cached.isPresent()) {
            log.info("Duplicate debit request detected for userId: {}", userId);
            return deserialize(cached.get());
        }

        // 2. Fetch wallet with optimistic lock
        Wallet wallet = walletRepository
                .findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + request.getUserId()
                ));

        // 3. Validate wallet status
        validateWalletActive(wallet);

        // 4. Validate sufficient balance
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: " + wallet.getBalance()
                            + " Requested: " + request.getAmount()
            );
        }

        // 5. Record balances before update
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter  = balanceBefore.subtract(request.getAmount());

        // 6. Update wallet balance
        wallet.setBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);

        // 7. Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .reference(generateReference())
                .type(TransactionType.DEBIT)
                .source(request.getSource())
                .status(TransactionStatus.SUCCESS)
                .amount(request.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(request.getDescription())
                .metadata(request.getMetadata())
                .build();

        WalletTransaction saved = transactionRepository.save(transaction);
        log.info("Debit successful — userId: {} amount: {} ref: {}",
                userId, request.getAmount(), saved.getReference());

        // 8. Build response
        WalletTransactionResponse response = mapToResponse(saved);

        // 9. Store in Redis for idempotency
        idempotencyService.storeResponse(userId, request.getIdempotencyKey(),
                serialize(response));

        // 10. Publish event
        walletEventService.publishDebitEvent(saved);

        return response;
    }

    // ─────────────────────────────────────────
    // REVERSE
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public WalletTransactionResponse reverse(String reference) {

        // 1. Find original transaction
        WalletTransaction original = transactionRepository
                .findByReference(reference)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + reference
                ));

        // 2. Validate it can be reversed
        if (original.getStatus() == TransactionStatus.REVERSED) {
            throw new WalletServiceException(
                    ErrorCode.TRANSACTION_ALREADY_REVERSED,
                    "Transaction already reversed: " + reference
            );
        }
        if (original.getStatus() != TransactionStatus.SUCCESS) {
            throw new WalletServiceException(ErrorCode.TRANSACTION_NOT_REVERSIBLE);
        }

        // 3. Fetch wallet with lock
        Wallet wallet = walletRepository
                .findByUserIdWithLock(original.getWallet().getUserId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for reversal"
                ));

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter;

        // 4. Reverse the original transaction direction
        if (original.getType() == TransactionType.CREDIT) {
            // Original was a credit → reverse with a debit
            if (wallet.getBalance().compareTo(original.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance to reverse credit"
                );
            }
            balanceAfter = balanceBefore.subtract(original.getAmount());
        } else {
            // Original was a debit → reverse with a credit
            balanceAfter = balanceBefore.add(original.getAmount());
        }

        // 5. Update wallet balance
        wallet.setBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);

        // 6. Mark original as reversed
        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        // 7. Create reversal transaction
        WalletTransaction reversal = WalletTransaction.builder()
                .wallet(wallet)
                .reference(generateReference())
                .type(original.getType() == TransactionType.CREDIT
                        ? TransactionType.DEBIT : TransactionType.CREDIT)
                .source(TransactionSource.REVERSAL)
                .status(TransactionStatus.SUCCESS)
                .amount(original.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description("Reversal of: " + reference)
                .metadata(original.getMetadata())
                .build();

        WalletTransaction saved = transactionRepository.save(reversal);
        log.info("Reversal successful for reference: {} new ref: {}",
                reference, saved.getReference());

        // 8. Publish event
        walletEventService.publishReversalEvent(saved);

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // GET BY REFERENCE
    // ─────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public WalletTransactionResponse getByReference(String reference) {
        return transactionRepository.findByReference(reference)
                .map(this::mapToResponse)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + reference
                ));
    }

    // ─────────────────────────────────────────
    // GET TRANSACTIONS (FILTERED + PAGED)
    // ─────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getTransactions(
            Long userId, TransactionFilterRequest filter) {

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + userId
                ));

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize());

        Page<WalletTransaction> page = transactionRepository.findByFilters(
                wallet.getId(),
                filter.getType(),
                filter.getSource(),
                filter.getStatus(),
                filter.getFrom(),
                filter.getTo(),
                pageable
        );

        List<WalletTransactionResponse> content = page.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return PageResponse.<WalletTransactionResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // ─────────────────────────────────────────
// GET ALL TRANSACTIONS (ADMIN)
// ─────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getAllTransactions(AdminTransactionFilterRequest filter) {

        Sort sort = filter.getSortDirection().equalsIgnoreCase("asc")
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<WalletTransaction> spec = TransactionSpecification.buildFilter(
                filter.getWalletId(),
                filter.getUserId(),
                filter.getType(),
                filter.getSource(),
                filter.getStatus(),
                filter.getFrom(),
                filter.getTo()
        );

        Page<WalletTransaction> page = transactionRepository.findAll(spec, pageable);

        log.info("Admin fetched all transactions - type: {}, status: {}, userId: {}",
                filter.getType(), filter.getStatus(), filter.getUserId());

        return PageResponse.from(page.map(this::mapToResponse));
    }


    // ─────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────
    private void validateWalletActive(Wallet wallet) {
        if (wallet.getStatus() == WalletStatus.SUSPENDED) {
            throw new WalletServiceException(ErrorCode.WALLET_SUSPENDED);
        }
        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new WalletServiceException(ErrorCode.WALLET_CLOSED);
        }
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletServiceException(
                    ErrorCode.WALLET_NOT_ACTIVE,
                    "Wallet is not active. Current status: " + wallet.getStatus()
            );
        }
    }

    private String generateReference() {
        return "WLT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private WalletTransactionResponse mapToResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder()
                .id(tx.getId())
                .reference(tx.getReference())
                .type(tx.getType())
                .source(tx.getSource())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .description(tx.getDescription())
                .metadata(tx.getMetadata())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize response: {}", e.getMessage());
            throw new WalletServiceException(ErrorCode.IDEMPOTENCY_SERIALIZE_ERROR);
        }
    }

    private WalletTransactionResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, WalletTransactionResponse.class);
        } catch (Exception e) {
            log.error("Failed to deserialize cached response: {}", e.getMessage());
            throw new WalletServiceException(ErrorCode.IDEMPOTENCY_DESERIALIZE_ERROR);
        }
    }
}