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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final WalletRepository            walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final IdempotencyService          idempotencyService;
    private final WalletEventService          walletEventService;
    private final ObjectMapper                objectMapper;
    private final TransactionTemplate         transactionTemplate;


    private static final int MAX_LOCK_ATTEMPTS = 3;

    // Result of one credit/debit attempt: either a new transaction, or one that
    // was already recorded for the same idempotency key.
    private record Outcome(WalletTransaction transaction, boolean created) {}


    // CREDIT
    @Override
    public WalletTransactionResponse credit(CreditWalletRequest request) {
        return processIdempotently(
                request.getUserId(),
                request.getIdempotencyKey(),
                () -> applyBalanceChange(
                        request.getUserId(), request.getIdempotencyKey(),
                        TransactionType.CREDIT, request.getAmount(), request.getSource(),
                        request.getDescription(), request.getMetadata()),
                walletEventService::publishCreditEvent
        );
    }


    // DEBIT
    @Override
    public WalletTransactionResponse debit(DebitWalletRequest request) {
        return processIdempotently(
                request.getUserId(),
                request.getIdempotencyKey(),
                () -> applyBalanceChange(
                        request.getUserId(), request.getIdempotencyKey(),
                        TransactionType.DEBIT, request.getAmount(), request.getSource(),
                        request.getDescription(), request.getMetadata()),
                walletEventService::publishDebitEvent
        );
    }


    // Idempotency flow shared by credit and debit:
    //  1. Redis is a fast-path cache of earlier responses.
    //  2. The DB unique key (wallet_id, idempotency_key) is the real guarantee.
    //  3. Redis is written and events are published only AFTER the DB commit,
    //     so a rolled-back attempt never looks "processed".
    private WalletTransactionResponse processIdempotently(Long userIdValue,
                                                          String idempotencyKey,
                                                          Supplier<Outcome> operation,
                                                          Consumer<WalletTransaction> publisher) {
        String userId = String.valueOf(userIdValue);

        var cached = idempotencyService.getProcessedResponse(userId, idempotencyKey);
        if (cached.isPresent()) {
            log.info("Duplicate request served from cache | userId={} key={}", userId, idempotencyKey);
            return deserialize(cached.get());
        }

        Outcome outcome;
        try {
            outcome = executeWithLockRetry(operation);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key committed first and the
            // unique key rejected ours, so return the transaction that won.
            WalletTransaction existing = transactionRepository
                    .findByUserIdAndIdempotencyKey(userIdValue, idempotencyKey)
                    .orElseThrow(() -> e);
            log.info("Concurrent duplicate rejected by DB | userId={} key={}", userId, idempotencyKey);
            outcome = new Outcome(existing, false);
        }

        WalletTransactionResponse response = mapToResponse(outcome.transaction());
        cacheResponse(userId, idempotencyKey, response);

        if (outcome.created()) {
            publisher.accept(outcome.transaction());
        }
        return response;
    }

    // Runs the operation in its own transaction, retrying when the wallet's
    // @Version check fails because another transaction updated it first.
    // Each retry re-reads the wallet, so it works on the latest balance.
    private Outcome executeWithLockRetry(Supplier<Outcome> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> operation.get());
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_LOCK_ATTEMPTS) {
                    log.warn("Optimistic lock conflict, giving up after {} attempts", attempt);
                    throw new WalletServiceException(ErrorCode.CONCURRENT_UPDATE);
                }
                log.warn("Optimistic lock conflict on attempt {}/{}, retrying",
                        attempt, MAX_LOCK_ATTEMPTS);
                backoff(attempt);
            }
        }
    }

    // Runs inside a transaction
    private Outcome applyBalanceChange(Long userId, String idempotencyKey,
                                       TransactionType type, BigDecimal amount,
                                       TransactionSource source, String description,
                                       String metadata) {

        // Redis may have missed (expired, evicted, or the write failed),
        // so check the database before applying anything
        Optional<WalletTransaction> existing =
                transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate request found in DB | userId={} key={}", userId, idempotencyKey);
            return new Outcome(existing.get(), false);
        }

        // Fetch wallet with optimistic lock
        Wallet wallet = walletRepository
                .findByUserIdWithLock(userId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + userId
                ));

        validateWalletActive(wallet);

        if (type == TransactionType.DEBIT && wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: " + wallet.getBalance()
                            + " Requested: " + amount
            );
        }

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter  = type == TransactionType.CREDIT
                ? balanceBefore.add(amount)
                : balanceBefore.subtract(amount);

        wallet.setBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .reference(generateReference())
                .idempotencyKey(idempotencyKey)
                .type(type)
                .source(source)
                .status(TransactionStatus.SUCCESS)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(description)
                .metadata(metadata)
                .build();

        WalletTransaction saved = transactionRepository.save(transaction);
        log.info("{} successful — userId: {} amount: {} ref: {}",
                type == TransactionType.CREDIT ? "Credit" : "Debit",
                userId, amount, saved.getReference());

        return new Outcome(saved, true);
    }

    // The DB already has the transaction, so a Redis failure must not fail the request
    private void cacheResponse(String userId, String idempotencyKey, WalletTransactionResponse response) {
        try {
            idempotencyService.storeResponse(userId, idempotencyKey, serialize(response));
        } catch (Exception e) {
            log.warn("Could not cache idempotency response | userId={} key={}: {}",
                    userId, idempotencyKey, e.getMessage());
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WalletServiceException(ErrorCode.CONCURRENT_UPDATE);
        }
    }


    // REVERSE

    @Override
    @Transactional
    public WalletTransactionResponse reverse(String reference) {

        //  Find original transaction
        WalletTransaction original = transactionRepository
                .findByReference(reference)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + reference
                ));

        //  Validate it can be reversed
        if (original.getStatus() == TransactionStatus.REVERSED) {
            throw new WalletServiceException(
                    ErrorCode.TRANSACTION_ALREADY_REVERSED,
                    "Transaction already reversed: " + reference
            );
        }
        if (original.getStatus() != TransactionStatus.SUCCESS) {
            throw new WalletServiceException(ErrorCode.TRANSACTION_NOT_REVERSIBLE);
        }

        //  Fetch wallet with lock
        Wallet wallet = walletRepository
                .findByUserIdWithLock(original.getWallet().getUserId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for reversal"
                ));

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter;

        // Reverse the original transaction direction
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

        //  Update wallet balance
        wallet.setBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);

        //  Mark original as reversed
        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        //  Create reversal transaction
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

        // Publish event
        walletEventService.publishReversalEvent(saved);

        return mapToResponse(saved);
    }

    // GET BY REFERENCE
    @Override
    @Transactional(readOnly = true)
    public WalletTransactionResponse getByReference(String reference) {
        return transactionRepository.findByReference(reference)
                .map(this::mapToResponse)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + reference
                ));
    }


    // Same "not found" for someone else's transaction, so references can't be probed
    @Override
    @Transactional(readOnly = true)
    public WalletTransactionResponse getByReferenceForUser(String reference, Long userId) {
        return transactionRepository.findByReference(reference)
                .filter(tx -> tx.getWallet().getUserId().equals(userId))
                .map(this::mapToResponse)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + reference
                ));
    }

    // GET TRANSACTIONS (FILTERED + PAGED)

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

// GET ALL TRANSACTIONS (ADMIN)
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


    // HELPERS
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