package com.gatepay.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.dto.*;
import com.gatepay.paymentservice.exception.DuplicateTransactionException;
import com.gatepay.paymentservice.exception.PaymentException;
import com.gatepay.paymentservice.exception.ReferenceNotFoundException;
import com.gatepay.paymentservice.helper.PaymentRequestHelper;
import com.gatepay.paymentservice.helper.UserValidationHelper;
import com.gatepay.paymentservice.model.AuditLog;
import com.gatepay.paymentservice.model.Payment;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.*;
import com.gatepay.paymentservice.repository.AuditLogRepository;
import com.gatepay.paymentservice.repository.PaymentRepository;
import com.gatepay.paymentservice.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.GET;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentStrategyContext paymentContext;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserValidationHelper userValidationHelper;
    private final PaymentRequestHelper paymentRequestHelper;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(30);

    // ======================================================
    // INITIALIZE PAYMENT
    // ======================================================

    @Override
    @Transactional
    public PaymentResponse initializePayment(
            PaymentRequest request,
            PaymentProvider provider,
            String ipAddress
    ) {
        log.info("Initializing payment | provider={} | ref={} | email={}",
                provider, request.getReference(), request.getEmail());

        // 1. Validate user
        userValidationHelper.validateUserForPayment(request.getUserId());

        // 2. Prepare request (reference, defaults, etc.)
        paymentRequestHelper.prepareRequest(request);

        String reference = request.getReference();

        try {
            // 3. FIRST: Check database before anything else
            Optional<PaymentTransaction> existingTx =
                    transactionRepository.findByReference(reference);

            if (existingTx.isPresent()) {
                log.info("Transaction already exists | ref={} | status={}",
                        reference, existingTx.get().getStatus());
                PaymentResponse response = handleExistingTransaction(
                        existingTx.get(), provider, request
                );
                cacheResponseSafely(reference, response);
                return response;
            }

            // 4. Check Redis cache for completed response
            Optional<String> cachedResponse = getCachedResponseSafely(reference);
            if (cachedResponse.isPresent()) {
                log.info("Returning cached response | ref={}", reference);
                return deserializeResponse(cachedResponse.get());
            }

            // 5. Try to acquire lock
            if (!tryAcquireLockSafely(reference)) {
                log.warn("Failed to acquire lock | ref={}", reference);

                // Wait a bit for the other request to complete
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Check cache again
                cachedResponse = getCachedResponseSafely(reference);
                if (cachedResponse.isPresent()) {
                    log.info("Found cached response after wait | ref={}", reference);
                    return deserializeResponse(cachedResponse.get());
                }

                // Check database again
                existingTx = transactionRepository.findByReference(reference);
                if (existingTx.isPresent()) {
                    log.info("Found transaction after wait | ref={}", reference);
                    PaymentResponse response = handleExistingTransaction(
                            existingTx.get(), provider, request
                    );
                    cacheResponseSafely(reference, response);
                    return response;
                }

                throw new PaymentException(
                        "Payment is being processed. Please wait a moment and try again."
                );
            }

            // Lock acquired successfully
            try {
                // 6. Triple-check database after acquiring lock
                existingTx = transactionRepository.findByReference(reference);
                if (existingTx.isPresent()) {
                    log.info("Transaction found after lock | ref={}", reference);
                    releaseLockSafely(reference);
                    PaymentResponse response = handleExistingTransaction(
                            existingTx.get(), provider, request
                    );
                    cacheResponseSafely(reference, response);
                    return response;
                }

                // 7. Call payment provider
                PaymentResponse response = paymentContext.processPayment(provider, request);

                if (!response.isSuccess()) {
                    releaseLockSafely(reference);
                    throw new PaymentException(response.getMessage());
                }

                // 8. Persist transaction with proper error handling
                try {
                    persistNewTransaction(request, response, provider, ipAddress);
                } catch (DataIntegrityViolationException e) {
                    // Duplicate caught by database constraint
                    log.warn("Duplicate caught by database | ref={}", reference);
                    releaseLockSafely(reference);

                    // Fetch the existing transaction
                    PaymentTransaction existingTransaction = transactionRepository
                            .findByReference(reference)
                            .orElseThrow(() -> new PaymentException(
                                    "Transaction conflict detected"
                            ));

                    PaymentResponse existingResponse =
                            buildResponseFromTransaction(existingTransaction);
                    cacheResponseSafely(reference, existingResponse);
                    return existingResponse;
                }

                // 9. Cache successful response
                cacheResponseSafely(reference, response);

                log.info("Payment initialized successfully | ref={}", reference);
                return response;

            } catch (DataIntegrityViolationException e) {
                // Catch any other database constraint violations
                log.warn("Database constraint violation | ref={}", reference, e);
                releaseLockSafely(reference);

                return transactionRepository.findByReference(reference)
                        .map(tx -> {
                            PaymentResponse response = buildResponseFromTransaction(tx);
                            cacheResponseSafely(reference, response);
                            return response;
                        })
                        .orElseThrow(() -> new PaymentException(
                                "Transaction conflict. Please try again."
                        ));

            } catch (Exception ex) {
                log.error("Payment initialization failed | ref={}", reference, ex);
                releaseLockSafely(reference);
                throw new PaymentException("Payment initialization failed: " + ex.getMessage());
            }

        } catch (PaymentException | DuplicateTransactionException e) {
            throw e; // Re-throw known exceptions
        } catch (Exception e) {
            log.error("Unexpected error during payment | ref={}", reference, e);
            throw new PaymentException("An unexpected error occurred. Please try again.");
        }
    }

    // ======================================================
    // HANDLE EXISTING TRANSACTION
    // ======================================================

    private PaymentResponse handleExistingTransaction(
            PaymentTransaction tx,
            PaymentProvider provider,
            PaymentRequest originalRequest
    ) {
        log.info("Handling existing transaction | ref={} | status={}",
                tx.getReference(), tx.getStatus());

        return switch (tx.getStatus()) {
            case PENDING -> {
                log.info("Returning pending transaction | ref={}", tx.getReference());
                yield buildResponseFromTransaction(tx);
            }

            case SUCCESS -> throw new DuplicateTransactionException(
                    "Payment already completed for reference " + tx.getReference()
            );

            case FAILED, CANCELLED -> {
                log.info("Retrying failed/cancelled transaction | ref={}", tx.getReference());
                yield retryFailedTransaction(tx, provider, originalRequest);
            }

            default -> throw new IllegalStateException(
                    "Unknown transaction status: " + tx.getStatus()
            );
        };
    }

    // ======================================================
    // RETRY FAILED TRANSACTION
    // ======================================================

    private PaymentResponse retryFailedTransaction(
            PaymentTransaction tx,
            PaymentProvider provider,
            PaymentRequest originalRequest
    ) {
        log.info("Retrying failed transaction | ref={}", tx.getReference());

        try {
            PaymentRequest retryRequest = PaymentRequest.builder()
                    .userId(tx.getUserId())
                    .walletId(originalRequest.getWalletId())
                    .email(tx.getCustomerEmail())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .reference(tx.getReference())
                    .callbackUrl(originalRequest.getCallbackUrl())
                    .customerName(tx.getCustomerName())
                    .phoneNumber(tx.getPhoneNumber())
                    .metadata(tx.getMetadata())
                    .build();

            PaymentResponse response = paymentContext.processPayment(provider, retryRequest);

            if (response.isSuccess()) {
                tx.setStatus(TransactionStatus.PENDING);
                tx.setAuthorizationUrl(response.getAuthorizationUrl());
                tx.setAccessCode(response.getAccessCode());
                tx.setGatewayResponse(null);
                transactionRepository.save(tx);

                log.info("Transaction retry successful | ref={}", tx.getReference());
            } else {
                log.warn("Transaction retry failed | ref={}", tx.getReference());
            }

            return response;

        } catch (Exception e) {
            log.error("Error retrying transaction | ref={}", tx.getReference(), e);
            throw new PaymentException("Failed to retry transaction: " + e.getMessage());
        }
    }

    // ======================================================
    // PERSIST TRANSACTION
    // ======================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistNewTransaction(
            PaymentRequest request,
            PaymentResponse response,
            PaymentProvider provider,
            String ipAddress
    ) {
        log.debug("Persisting new transaction | ref={}", request.getReference());

        // Final check before insert
        if (transactionRepository.existsByReference(request.getReference())) {
            log.warn("Transaction already exists, skipping insert | ref={}",
                    request.getReference());
            throw new DataIntegrityViolationException("Transaction already exists");
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .reference(request.getReference())
                .userId(request.getUserId())
                .provider(provider)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .customerEmail(request.getEmail())
                .customerName(request.getCustomerName())
                .phoneNumber(request.getPhoneNumber())
                .authorizationUrl(response.getAuthorizationUrl())
                .accessCode(response.getAccessCode())
                .metadata(request.getMetadata())
                .ipAddress(ipAddress)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            transactionRepository.save(transaction);
            log.info("Transaction persisted successfully | ref={}", transaction.getReference());
        } catch (DataIntegrityViolationException e) {
            log.error("Duplicate entry caught during save | ref={}", request.getReference());
            throw e; // Re-throw to be caught by caller
        }
    }

    // ======================================================
    // SAFE REDIS OPERATIONS
    // ======================================================

    private boolean tryAcquireLockSafely(String reference) {
        try {
            return idempotencyService.tryAcquireLock(reference, IDEMPOTENCY_TTL);
        } catch (Exception e) {
            log.error("Failed to acquire lock | ref={}", reference, e);
            return false;
        }
    }

    private Optional<String> getCachedResponseSafely(String reference) {
        try {
            return idempotencyService.getResponse(reference);
        } catch (Exception e) {
            log.warn("Failed to get cached response | ref={}", reference, e);
            return Optional.empty();
        }
    }

    private void cacheResponseSafely(String reference, PaymentResponse response) {
        try {
            String serialized = objectMapper.writeValueAsString(response);
            idempotencyService.saveResponse(reference, serialized, IDEMPOTENCY_TTL);
            log.debug("Response cached successfully | ref={}", reference);
        } catch (Exception e) {
            log.warn("Failed to cache response | ref={}", reference, e);
        }
    }

    private void releaseLockSafely(String reference) {
        try {
            idempotencyService.releaseLock(reference);
            log.debug("Lock released | ref={}", reference);
        } catch (Exception e) {
            log.warn("Failed to release lock | ref={}", reference, e);
        }
    }

    private PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (Exception e) {
            log.error("Failed to deserialize cached response", e);
            throw new PaymentException("Failed to retrieve cached payment response");
        }
    }

    private PaymentResponse buildResponseFromTransaction(PaymentTransaction tx) {
        return PaymentResponse.builder()
                .success(true)
                .message("Payment already initiated")
                .reference(tx.getReference())
                .authorizationUrl(tx.getAuthorizationUrl())
                .accessCode(tx.getAccessCode())
                .provider(tx.getProvider().name())
                .build();
    }

    // ======================================================
    // VERIFY PAYMENT
    // ======================================================

    @Override
    @Transactional
    public VerifyPaymentResponse verifyPayment(
            VerifyPaymentRequest request,
            String performedBy,
            String ipAddress,
            String userAgent
    ) {
        String reference = request.getReference();
        PaymentProvider provider = request.getProvider();

        log.info("Verifying payment | ref={} | provider={}", reference, provider);

        VerifyPaymentResponse response =
                paymentContext.verifyPayment(provider, reference);

        if (response == null || response.getData() == null) {
            throw new PaymentException("Invalid verification response");
        }

        PaymentTransaction tx = transactionRepository
                .findByReference(reference)
                .orElseThrow(() ->
                        new ReferenceNotFoundException("Transaction not found: " + reference));

        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            log.info("Transaction already successful | ref={}", reference);
            return response;
        }

        TransactionStatus newStatus =
                mapToInternalStatus(response.getData().getStatus());

        Map<String, Object> oldData = buildAuditData(tx);

        updateTransaction(tx, response, newStatus);
        transactionRepository.save(tx);

        if (newStatus == TransactionStatus.SUCCESS) {
            createPaymentRecordIfAbsent(tx, provider);
        }

        saveAuditLog(tx, performedBy, ipAddress, userAgent, oldData, newStatus);

        log.info("Payment verified | ref={} | newStatus={}", reference, newStatus);

        return response;
    }

    // ======================================================
    // HELPER METHODS
    // ======================================================

    private void updateTransaction(
            PaymentTransaction tx,
            VerifyPaymentResponse response,
            TransactionStatus status
    ) {
        tx.setStatus(status);
        tx.setGatewayResponse(response.getData().getGatewayResponse());
        tx.setProviderTransactionId(
                response.getData().getId() != null
                        ? String.valueOf(response.getData().getId())
                        : null
        );

        if (status == TransactionStatus.SUCCESS) {
            tx.setPaidAt(LocalDateTime.now());
        }
    }

    private void createPaymentRecordIfAbsent(
            PaymentTransaction tx,
            PaymentProvider provider
    ) {
        paymentRepository.findByReference(tx.getReference())
                .orElseGet(() -> paymentRepository.save(
                        Payment.builder()
                                .reference(tx.getReference())
                                .amount(tx.getAmount())
                                .userId(tx.getUserId())
                                .paymentProvider(provider)
                                .status(TransactionStatus.SUCCESS)
                                .narration("Payment via " + provider.name())
                                .createdAt(LocalDateTime.now())
                                .build()
                ));
    }

    private Map<String, Object> buildAuditData(PaymentTransaction tx) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", tx.getStatus());
        data.put("gatewayResponse", tx.getGatewayResponse());
        data.put("paidAt",
                tx.getPaidAt() != null
                        ? tx.getPaidAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null);
        return data;
    }

    private void saveAuditLog(
            PaymentTransaction tx,
            String performedBy,
            String ipAddress,
            String userAgent,
            Map<String, Object> oldData,
            TransactionStatus newStatus
    ) {
        auditLogRepository.save(
                AuditLog.builder()
                        .entityName("PaymentTransaction")
                        .entityId(tx.getId())
                        .action(AuditAction.VERIFY)
                        .status(newStatus == TransactionStatus.SUCCESS
                                ? AuditStatus.SUCCESS
                                : AuditStatus.FAILED)
                        .performedBy(performedBy)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .oldData(oldData)
                        .newData(buildAuditData(tx))
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    private TransactionStatus mapToInternalStatus(String providerStatus) {
        return switch (providerStatus.toUpperCase()) {
            case "SUCCESS", "SUCCESSFUL", "PAID", "COMPLETED" -> TransactionStatus.SUCCESS;
            case "PENDING", "PROCESSING" -> TransactionStatus.PENDING;
            case "CANCELLED" -> TransactionStatus.CANCELLED;
            default -> TransactionStatus.FAILED;
        };
    }

    // ======================================================
    // READ OPERATIONS
    // ======================================================

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> fetchAllTransactions(int page, int size) {
        Pageable pageable =
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return transactionRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> fetchUserTransactions(String email, int page, int size) {
        Pageable pageable =
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return transactionRepository.findByCustomerEmail(email, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransaction fetchTransactionByReference(String reference) {
        return transactionRepository.findByReference(reference)
                .orElseThrow(() ->
                        new EntityNotFoundException("Transaction not found: " + reference));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> fetchUserTransactionsByUserId(
            String userId,
            TransactionFilter filter,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        TransactionStatus status = (filter != null) ? filter.getStatus() : null;
        TransactionType transactionType = (filter != null) ? filter.getTransactionType() : null;

        LocalDateTime startDate = (filter != null && filter.getStartDate() != null)
                ? filter.getStartDate().atStartOfDay()
                : null;

        LocalDateTime endDate = (filter != null && filter.getEndDate() != null)
                ? filter.getEndDate().atTime(23, 59, 59)
                : null;

        return transactionRepository.findTransactionsByUserId(
                userId,
                pageable
        );
    }

}