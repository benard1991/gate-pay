//package com.gatepay.paymentservice.service;
//
//import com.gatepay.paymentservice.dto.*;
//import com.gatepay.paymentservice.exception.DuplicateTransactionException;
//import com.gatepay.paymentservice.exception.PaymentException;
//import com.gatepay.paymentservice.exception.ReferenceNotFoundException;
//import com.gatepay.paymentservice.helper.PaymentRequestHelper;
//import com.gatepay.paymentservice.helper.UserValidationHelper;
//import com.gatepay.paymentservice.model.AuditLog;
//import com.gatepay.paymentservice.model.Payment;
//import com.gatepay.paymentservice.model.PaymentTransaction;
//import com.gatepay.paymentservice.model.enums.*;
//import com.gatepay.paymentservice.repository.AuditLogRepository;
//import com.gatepay.paymentservice.repository.PaymentRepository;
//import com.gatepay.paymentservice.repository.PaymentTransactionRepository;
//import jakarta.persistence.EntityNotFoundException;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.dao.DataIntegrityViolationException;
//import org.springframework.data.domain.*;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class PaymentServiceImpl implements PaymentService {
//
//    private final PaymentStrategyContext paymentContext;
//    private final PaymentTransactionRepository transactionRepository;
//    private final PaymentRepository paymentRepository;
//    private final AuditLogRepository auditLogRepository;
//    private final UserValidationHelper userValidationHelper;
//    private final PaymentRequestHelper paymentRequestHelper;
//
//    // ======================================================
//    // INITIALIZE PAYMENT
//    // ======================================================
//
//    @Override
//    @Transactional
//    public PaymentResponse initializePayment(
//            PaymentRequest request,
//            PaymentProvider provider,
//            String ipAddress
//    ) {
//        log.info("Initializing payment | provider={} | ref={} | email={}",
//                provider, request.getReference(), request.getEmail());
//
//        // 1. Validate user
//        userValidationHelper.validateUserForPayment(request.getUserId());
//
//        // 2. Prepare request (reference, defaults, etc.)
//        paymentRequestHelper.prepareRequest(request);
//
//        // 3. Idempotency check
//        Optional<PaymentTransaction> existing =
//                transactionRepository.findByReference(request.getReference());
//
//        if (existing.isPresent()) {
//            return handleExistingTransaction(existing.get(), provider, request);
//        }
//
//        try {
//            // 4. Call payment provider (NO DB mutation yet)
//            PaymentResponse response =
//                    paymentContext.processPayment(provider, request);
//
//            if (!response.isSuccess()) {
//                throw new PaymentException(response.getMessage());
//            }
//
//            // 5. Persist transaction (isolated transaction)
//            persistNewTransaction(request, response, provider, ipAddress);
//
//            return response;
//
//        } catch (DataIntegrityViolationException e) {
//            // Race condition protection
//            log.warn("Race condition detected | ref={}", request.getReference());
//
//            return transactionRepository.findByReference(request.getReference())
//                    .map(this::buildResponseFromTransaction)
//                    .orElseThrow(() -> new PaymentException("Duplicate transaction detected"));
//        } catch (Exception ex) {
//            log.error("Payment initialization failed | ref={}", request.getReference(), ex);
//            throw new PaymentException("Payment initialization failed: " + ex.getMessage());
//        }
//    }
//
//    // ======================================================
//    // HANDLE EXISTING TRANSACTION
//    // ======================================================
//
//    private PaymentResponse handleExistingTransaction(
//            PaymentTransaction tx,
//            PaymentProvider provider,
//            PaymentRequest originalRequest
//    ) {
//        log.info("Existing transaction | ref={} | status={}",
//                tx.getReference(), tx.getStatus());
//
//        return switch (tx.getStatus()) {
//            case PENDING -> buildResponseFromTransaction(tx);
//
//            case SUCCESS -> throw new DuplicateTransactionException(
//                    "Payment already completed for reference " + tx.getReference()
//            );
//
//            case FAILED, CANCELLED -> retryFailedTransaction(tx, provider, originalRequest);
//
//            default -> throw new IllegalStateException(
//                    "Unknown transaction status: " + tx.getStatus()
//            );
//        };
//    }
//
//    // ======================================================
//    // RETRY FAILED TRANSACTION
//    // ======================================================
//
//    private PaymentResponse retryFailedTransaction(
//            PaymentTransaction tx,
//            PaymentProvider provider,
//            PaymentRequest originalRequest
//    ) {
//        log.info("Retrying transaction | ref={}", tx.getReference());
//
//        try {
//            // Build retry request from original transaction + original request data
//            PaymentRequest retryRequest = PaymentRequest.builder()
//                    .userId(tx.getUserId())
//                    .walletId(originalRequest.getWalletId()) // Use from original request
//                    .email(tx.getCustomerEmail())
//                    .amount(tx.getAmount())
//                    .currency(tx.getCurrency())
//                    .reference(tx.getReference())
//                    .callbackUrl(originalRequest.getCallbackUrl()) // Use from original request
//                    .customerName(tx.getCustomerName())
//                    .phoneNumber(tx.getPhoneNumber())
//                    .metadata(tx.getMetadata())
//                    .build();
//
//            PaymentResponse response =
//                    paymentContext.processPayment(provider, retryRequest);
//
//            if (response.isSuccess()) {
//                // Update existing transaction with new payment details
//                tx.setStatus(TransactionStatus.PENDING);
//                tx.setAuthorizationUrl(response.getAuthorizationUrl());
//                tx.setAccessCode(response.getAccessCode());
//                tx.setGatewayResponse(null); // Clear old gateway response
//                transactionRepository.save(tx);
//
//                log.info("Transaction retry successful | ref={}", tx.getReference());
//            } else {
//                log.warn("Transaction retry failed | ref={} | message={}",
//                        tx.getReference(), response.getMessage());
//            }
//
//            return response;
//
//        } catch (Exception e) {
//            log.error("Error retrying transaction | ref={}", tx.getReference(), e);
//            throw new PaymentException("Failed to retry transaction: " + e.getMessage());
//        }
//    }
//
//    // ======================================================
//    // PERSIST TRANSACTION (NEW TX)
//    // ======================================================
//
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    protected void persistNewTransaction(
//            PaymentRequest request,
//            PaymentResponse response,
//            PaymentProvider provider,
//            String ipAddress
//    ) {
//        try {
//            PaymentTransaction transaction = PaymentTransaction.builder()
//                    .reference(request.getReference())
//                    .userId(request.getUserId())
//                    .provider(provider)
//                    .status(TransactionStatus.PENDING)
//                    .amount(request.getAmount())
//                    .currency(request.getCurrency())
//                    .customerEmail(request.getEmail())
//                    .customerName(request.getCustomerName())
//                    .phoneNumber(request.getPhoneNumber())
//                    .authorizationUrl(response.getAuthorizationUrl())
//                    .accessCode(response.getAccessCode())
//                    .metadata(request.getMetadata())
//                    .ipAddress(ipAddress)
//                    .createdAt(LocalDateTime.now())
//                    .build();
//
//            transactionRepository.save(transaction);
//
//            log.info("Transaction persisted | ref={}", transaction.getReference());
//
//        } catch (Exception e) {
//            log.error("Failed to persist transaction | ref={}", request.getReference(), e);
//            throw new PaymentException("Failed to save transaction: " + e.getMessage());
//        }
//    }
//
//    // ======================================================
//    // VERIFY PAYMENT
//    // ======================================================
//
//    @Override
//    @Transactional
//    public VerifyPaymentResponse verifyPayment(
//            VerifyPaymentRequest request,
//            String performedBy,
//            String ipAddress,
//            String userAgent
//    ) {
//        String reference = request.getReference();
//        PaymentProvider provider = request.getProvider();
//
//        VerifyPaymentResponse response =
//                paymentContext.verifyPayment(provider, reference);
//
//        if (response == null || response.getData() == null) {
//            throw new PaymentException("Invalid verification response");
//        }
//
//        PaymentTransaction tx = transactionRepository
//                .findByReference(reference)
//                .orElseThrow(() ->
//                        new ReferenceNotFoundException("Transaction not found: " + reference));
//
//        if (tx.getStatus() == TransactionStatus.SUCCESS) {
//            return response;
//        }
//
//        TransactionStatus newStatus =
//                mapToInternalStatus(response.getData().getStatus());
//
//        Map<String, Object> oldData = buildAuditData(tx);
//
//        updateTransaction(tx, response, newStatus);
//        transactionRepository.save(tx);
//
//        if (newStatus == TransactionStatus.SUCCESS) {
//            createPaymentRecordIfAbsent(tx, provider);
//        }
//
//        saveAuditLog(tx, performedBy, ipAddress, userAgent, oldData, newStatus);
//
//        return response;
//    }
//
//    // ======================================================
//    // HELPERS
//    // ======================================================
//
//    private void updateTransaction(
//            PaymentTransaction tx,
//            VerifyPaymentResponse response,
//            TransactionStatus status
//    ) {
//        tx.setStatus(status);
//        tx.setGatewayResponse(response.getData().getGatewayResponse());
//        tx.setProviderTransactionId(
//                response.getData().getId() != null
//                        ? String.valueOf(response.getData().getId())
//                        : null
//        );
//
//        if (status == TransactionStatus.SUCCESS) {
//            tx.setPaidAt(LocalDateTime.now());
//        }
//    }
//
//    private void createPaymentRecordIfAbsent(
//            PaymentTransaction tx,
//            PaymentProvider provider
//    ) {
//        paymentRepository.findByReference(tx.getReference())
//                .orElseGet(() -> paymentRepository.save(
//                        Payment.builder()
//                                .reference(tx.getReference())
//                                .amount(tx.getAmount())
//                                .userId(tx.getUserId())
//                                .paymentProvider(provider)
//                                .status(TransactionStatus.SUCCESS)
//                                .narration("Payment via " + provider.name())
//                                .createdAt(LocalDateTime.now())
//                                .build()
//                ));
//    }
//
//    private PaymentResponse buildResponseFromTransaction(PaymentTransaction tx) {
//        return PaymentResponse.builder()
//                .success(true)
//                .message("Payment already initiated")
//                .reference(tx.getReference())
//                .authorizationUrl(tx.getAuthorizationUrl())
//                .accessCode(tx.getAccessCode())
//                .provider(tx.getProvider().name())
//                .build();
//    }
//
//    private Map<String, Object> buildAuditData(PaymentTransaction tx) {
//        Map<String, Object> data = new HashMap<>();
//        data.put("status", tx.getStatus());
//        data.put("gatewayResponse", tx.getGatewayResponse());
//        data.put("paidAt",
//                tx.getPaidAt() != null
//                        ? tx.getPaidAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
//                        : null);
//        return data;
//    }
//
//    private void saveAuditLog(
//            PaymentTransaction tx,
//            String performedBy,
//            String ipAddress,
//            String userAgent,
//            Map<String, Object> oldData,
//            TransactionStatus newStatus
//    ) {
//        auditLogRepository.save(
//                AuditLog.builder()
//                        .entityName("PaymentTransaction")
//                        .entityId(tx.getId())
//                        .action(AuditAction.VERIFY)
//                        .status(newStatus == TransactionStatus.SUCCESS
//                                ? AuditStatus.SUCCESS
//                                : AuditStatus.FAILED)
//                        .performedBy(performedBy)
//                        .ipAddress(ipAddress)
//                        .userAgent(userAgent)
//                        .oldData(oldData)
//                        .newData(buildAuditData(tx))
//                        .createdAt(LocalDateTime.now())
//                        .build()
//        );
//    }
//
//    private TransactionStatus mapToInternalStatus(String providerStatus) {
//        return switch (providerStatus.toUpperCase()) {
//            case "SUCCESS", "PAID", "COMPLETED" -> TransactionStatus.SUCCESS;
//            case "PENDING", "PROCESSING" -> TransactionStatus.PENDING;
//            case "CANCELLED" -> TransactionStatus.CANCELLED;
//            default -> TransactionStatus.FAILED;
//        };
//    }
//
//    // ======================================================
//    // READ OPERATIONS
//    // ======================================================
//
//    @Override
//    @Transactional(readOnly = true)
//    public Page<PaymentTransaction> fetchAllTransactions(int page, int size) {
//        Pageable pageable =
//                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//        return transactionRepository.findAll(pageable);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public Page<PaymentTransaction> fetchUserTransactions(String email, int page, int size) {
//        Pageable pageable =
//                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//        return transactionRepository.findByCustomerEmail(email, pageable);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PaymentTransaction fetchTransactionByReference(String reference) {
//        return transactionRepository.findByReference(reference)
//                .orElseThrow(() ->
//                        new EntityNotFoundException("Transaction not found: " + reference));
//    }
//}