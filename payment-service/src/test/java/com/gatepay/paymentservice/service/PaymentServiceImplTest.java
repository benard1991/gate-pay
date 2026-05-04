package com.gatepay.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.dto.*;
import com.gatepay.paymentservice.dto.UserDto;
import com.gatepay.paymentservice.exception.DuplicateTransactionException;
import com.gatepay.paymentservice.exception.PaymentException;
import com.gatepay.paymentservice.exception.ReferenceNotFoundException;
import com.gatepay.paymentservice.helper.PaymentRequestHelper;
import com.gatepay.paymentservice.helper.UserValidationHelper;
import com.gatepay.paymentservice.model.AuditLog;
import com.gatepay.paymentservice.model.Payment;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.producer.PaymentEventProducer;
import com.gatepay.paymentservice.repository.AuditLogRepository;
import com.gatepay.paymentservice.repository.PaymentRepository;
import com.gatepay.paymentservice.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Unit Tests")
class PaymentServiceImplTest {

    @Mock private PaymentStrategyContext paymentContext;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserValidationHelper userValidationHelper;
    @Mock private PaymentRequestHelper paymentRequestHelper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private ObjectMapper objectMapper;
    @Mock private PaymentEventProducer paymentEventProducer;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // SHARED FIXTURES

    private PaymentRequest buildRequest() {
        return PaymentRequest.builder()
                .userId(1L)
                .walletId(10L)
                .email("user@example.com")
                .amount(new BigDecimal("5000.00"))
                .currency("NGN")
                .reference("REF-001")
                .callbackUrl("https://example.com/callback")
                .customerName("John Doe")
                .phoneNumber("08012345678")
                .build();
    }

    private PaymentResponse buildSuccessResponse() {
        return PaymentResponse.builder()
                .success(true)
                .message("Payment initialized")
                .reference("REF-001")
                .authorizationUrl("https://paystack.com/pay/abc")
                .accessCode("ACCESS-123")
                .provider(PaymentProvider.PAYSTACK.name())
                .build();
    }

    private PaymentTransaction buildTransaction(TransactionStatus status) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(1L);
        tx.setReference("REF-001");
        tx.setUserId(1L);
        tx.setStatus(status);
        tx.setAmount(new BigDecimal("5000.00"));
        tx.setCurrency("NGN");
        tx.setCustomerEmail("user@example.com");
        tx.setCustomerName("John Doe");
        tx.setPhoneNumber("08012345678");
        tx.setProvider(PaymentProvider.PAYSTACK);
        tx.setAuthorizationUrl("https://paystack.com/pay/abc");
        tx.setAccessCode("ACCESS-123");
        tx.setCreatedAt(LocalDateTime.now());
        return tx;
    }

    private VerifyPaymentResponse buildVerifyResponse(String status) {
        VerifyPaymentResponse.TransactionData data = VerifyPaymentResponse.TransactionData.builder()
                .id(99L)
                .status(status)
                .reference("REF-001")
                .amount(new BigDecimal("5000.00"))
                .currency("NGN")
                .gatewayResponse("Approved")
                .build();

        return VerifyPaymentResponse.builder()
                .success(true)
                .message("Verification successful")
                .provider(PaymentProvider.PAYSTACK.name())
                .data(data)
                .build();
    }

    // initializePayment

    @Nested
    @DisplayName("initializePayment")
    class InitializePaymentTests {

        @Test
        @DisplayName("should initialize payment successfully when no existing transaction")
        void should_initialize_payment_successfully() throws Exception {
            PaymentRequest request = buildRequest();
            PaymentResponse response = buildSuccessResponse();

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.getResponse("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.tryAcquireLock(eq("REF-001"), any())).thenReturn(true);
            when(paymentContext.processPayment(PaymentProvider.PAYSTACK, request)).thenReturn(response);
            when(transactionRepository.existsByReference("REF-001")).thenReturn(false);
            when(objectMapper.writeValueAsString(response)).thenReturn("{\"success\":true}");

            PaymentResponse result = paymentService.initializePayment(
                    request, PaymentProvider.PAYSTACK, "127.0.0.1"
            );

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getReference()).isEqualTo("REF-001");
            verify(transactionRepository).save(any(PaymentTransaction.class));
        }

        @Test
        @DisplayName("should return existing PENDING transaction without calling provider")
        void should_return_existing_pending_transaction() {
            PaymentRequest request = buildRequest();
            PaymentTransaction pendingTx = buildTransaction(TransactionStatus.PENDING);

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(pendingTx));

            PaymentResponse result = paymentService.initializePayment(
                    request, PaymentProvider.PAYSTACK, "127.0.0.1"
            );

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Payment already initiated");
            verify(paymentContext, never()).processPayment(any(), any());
        }

        @Test
        @DisplayName("should throw DuplicateTransactionException for SUCCESS transaction")
        void should_throw_for_existing_success_transaction() {
            PaymentRequest request = buildRequest();
            PaymentTransaction successTx = buildTransaction(TransactionStatus.SUCCESS);

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(successTx));

            assertThatThrownBy(() ->
                    paymentService.initializePayment(request, PaymentProvider.PAYSTACK, "127.0.0.1")
            ).isInstanceOf(DuplicateTransactionException.class)
                    .hasMessageContaining("REF-001");
        }

        @Test
        @DisplayName("should return cached response when available")
        void should_return_cached_response() throws Exception {
            PaymentRequest request = buildRequest();
            PaymentResponse cachedResponse = buildSuccessResponse();
            String cachedJson = "{\"success\":true,\"reference\":\"REF-001\"}";

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.getResponse("REF-001")).thenReturn(Optional.of(cachedJson));
            when(objectMapper.readValue(cachedJson, PaymentResponse.class)).thenReturn(cachedResponse);

            PaymentResponse result = paymentService.initializePayment(
                    request, PaymentProvider.PAYSTACK, "127.0.0.1"
            );

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            verify(paymentContext, never()).processPayment(any(), any());
        }

        @Test
        @DisplayName("should throw PaymentException when provider returns failure")
        void should_throw_when_provider_returns_failure() {
            PaymentRequest request = buildRequest();
            PaymentResponse failResponse = PaymentResponse.builder()
                    .success(false)
                    .message("Provider rejected the request")
                    .build();

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.getResponse("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.tryAcquireLock(eq("REF-001"), any())).thenReturn(true);
            when(paymentContext.processPayment(PaymentProvider.PAYSTACK, request))
                    .thenReturn(failResponse);

            assertThatThrownBy(() ->
                    paymentService.initializePayment(request, PaymentProvider.PAYSTACK, "127.0.0.1")
            ).isInstanceOf(PaymentException.class);

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should handle database constraint violation and return existing transaction")
        void should_handle_db_constraint_violation() throws Exception {
            PaymentRequest request = buildRequest();
            PaymentTransaction existingTx = buildTransaction(TransactionStatus.PENDING);

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            // First call: empty (no existing tx), second call (triple-check after lock): empty,
            // third call (after DataIntegrityViolation): returns existingTx
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existingTx));
            when(idempotencyService.getResponse("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.tryAcquireLock(eq("REF-001"), any())).thenReturn(true);
            when(paymentContext.processPayment(eq(PaymentProvider.PAYSTACK), any(PaymentRequest.class)))
                    .thenReturn(buildSuccessResponse());
            when(transactionRepository.existsByReference("REF-001")).thenReturn(false);
            doThrow(DataIntegrityViolationException.class)
                    .when(transactionRepository).save(any(PaymentTransaction.class));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            PaymentResponse result = paymentService.initializePayment(
                    request, PaymentProvider.PAYSTACK, "127.0.0.1"
            );

            assertThat(result).isNotNull();
            assertThat(result.getMessage()).isEqualTo("Payment already initiated");
        }

        @Test
        @DisplayName("should throw PaymentException when lock cannot be acquired and no fallback found")
        void should_throw_when_lock_not_acquired_and_no_fallback() {
            PaymentRequest request = buildRequest();

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.getResponse("REF-001")).thenReturn(Optional.empty());
            when(idempotencyService.tryAcquireLock(eq("REF-001"), any())).thenReturn(false);

            assertThatThrownBy(() ->
                    paymentService.initializePayment(request, PaymentProvider.PAYSTACK, "127.0.0.1")
            ).isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Payment is being processed");
        }

        @Test
        @DisplayName("should retry FAILED transaction and return new response")
        void should_retry_failed_transaction() throws Exception {
            PaymentRequest request = buildRequest();
            PaymentTransaction failedTx = buildTransaction(TransactionStatus.FAILED);
            PaymentResponse retryResponse = buildSuccessResponse();

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(failedTx));
            when(paymentContext.processPayment(eq(PaymentProvider.PAYSTACK), any(PaymentRequest.class)))
                    .thenReturn(retryResponse);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            PaymentResponse result = paymentService.initializePayment(
                    request, PaymentProvider.PAYSTACK, "127.0.0.1"
            );

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            verify(transactionRepository).save(failedTx);
        }

        @Test
        @DisplayName("should retry CANCELLED transaction")
        void should_retry_cancelled_transaction() throws Exception {
            PaymentRequest request = buildRequest();
            PaymentTransaction cancelledTx = buildTransaction(TransactionStatus.CANCELLED);
            PaymentResponse retryResponse = buildSuccessResponse();

            when(userValidationHelper.validateUserForPayment(1L)).thenReturn(new UserDto());
            doNothing().when(paymentRequestHelper).prepareRequest(request);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(cancelledTx));
            when(paymentContext.processPayment(eq(PaymentProvider.PAYSTACK), any(PaymentRequest.class)))
                    .thenReturn(retryResponse);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            PaymentResponse result = paymentService.initializePayment(
                    request, PaymentProvider.PAYSTACK, "127.0.0.1"
            );

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // verifyPayment

    @Nested
    @DisplayName("verifyPayment")
    class VerifyPaymentTests {

        private VerifyPaymentRequest buildVerifyRequest() {
            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setReference("REF-001");
            req.setProvider(PaymentProvider.PAYSTACK);
            return req;
        }

        @Test
        @DisplayName("should verify payment successfully and update status to SUCCESS")
        void should_verify_payment_successfully() {
            VerifyPaymentRequest request = buildVerifyRequest();
            VerifyPaymentResponse verifyResponse = buildVerifyResponse("success");
            PaymentTransaction tx = buildTransaction(TransactionStatus.PENDING);

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(verifyResponse);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(tx));
            when(paymentRepository.findByReference("REF-001")).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenReturn(new Payment());

            VerifyPaymentResponse result = paymentService.verifyPayment(
                    request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
            );

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            verify(transactionRepository).save(tx);
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            verify(paymentEventProducer).sendPaymentSuccessEvent(any());
            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("should return early when transaction already SUCCESS")
        void should_return_early_when_already_success() {
            VerifyPaymentRequest request = buildVerifyRequest();
            VerifyPaymentResponse verifyResponse = buildVerifyResponse("success");
            PaymentTransaction tx = buildTransaction(TransactionStatus.SUCCESS);

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(verifyResponse);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(tx));

            VerifyPaymentResponse result = paymentService.verifyPayment(
                    request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
            );

            assertThat(result).isNotNull();
            verify(transactionRepository, never()).save(any());
            verify(paymentEventProducer, never()).sendPaymentSuccessEvent(any());
        }

        @Test
        @DisplayName("should update status to FAILED when provider returns failed")
        void should_update_status_to_failed() {
            VerifyPaymentRequest request = buildVerifyRequest();
            VerifyPaymentResponse verifyResponse = buildVerifyResponse("failed");
            PaymentTransaction tx = buildTransaction(TransactionStatus.PENDING);

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(verifyResponse);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(tx));

            paymentService.verifyPayment(
                    request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
            );

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
            verify(paymentEventProducer, never()).sendPaymentSuccessEvent(any());
            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("should update status to PENDING when provider returns pending")
        void should_update_status_to_pending() {
            VerifyPaymentRequest request = buildVerifyRequest();
            VerifyPaymentResponse verifyResponse = buildVerifyResponse("pending");
            PaymentTransaction tx = buildTransaction(TransactionStatus.PENDING);

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(verifyResponse);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(tx));

            paymentService.verifyPayment(
                    request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
            );

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
            verify(paymentEventProducer, never()).sendPaymentSuccessEvent(any());
        }

        @Test
        @DisplayName("should throw ReferenceNotFoundException when transaction not found")
        void should_throw_when_transaction_not_found() {
            VerifyPaymentRequest request = buildVerifyRequest();
            VerifyPaymentResponse verifyResponse = buildVerifyResponse("success");

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(verifyResponse);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.verifyPayment(
                            request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
                    )
            ).isInstanceOf(ReferenceNotFoundException.class)
                    .hasMessageContaining("REF-001");
        }

        @Test
        @DisplayName("should throw PaymentException when verify response is null")
        void should_throw_when_verify_response_null() {
            VerifyPaymentRequest request = buildVerifyRequest();

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(null);

            assertThatThrownBy(() ->
                    paymentService.verifyPayment(
                            request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
                    )
            ).isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Invalid verification response");
        }

        @Test
        @DisplayName("should not create duplicate Payment record if one already exists")
        void should_not_create_duplicate_payment_record() {
            VerifyPaymentRequest request = buildVerifyRequest();
            VerifyPaymentResponse verifyResponse = buildVerifyResponse("success");
            PaymentTransaction tx = buildTransaction(TransactionStatus.PENDING);
            Payment existingPayment = new Payment();

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(verifyResponse);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(tx));
            when(paymentRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(existingPayment));

            paymentService.verifyPayment(
                    request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
            );

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should save audit log on every verification")
        void should_save_audit_log() {
            VerifyPaymentRequest request = buildVerifyRequest();
            VerifyPaymentResponse verifyResponse = buildVerifyResponse("success");
            PaymentTransaction tx = buildTransaction(TransactionStatus.PENDING);

            when(paymentContext.verifyPayment(PaymentProvider.PAYSTACK, "REF-001"))
                    .thenReturn(verifyResponse);
            when(transactionRepository.findByReference("REF-001"))
                    .thenReturn(Optional.of(tx));
            when(paymentRepository.findByReference("REF-001")).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenReturn(new Payment());

            paymentService.verifyPayment(
                    request, "admin@example.com", "127.0.0.1", "Mozilla/5.0"
            );

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            AuditLog log = captor.getValue();
            assertThat(log.getPerformedBy()).isEqualTo("admin@example.com");
            assertThat(log.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(log.getUserAgent()).isEqualTo("Mozilla/5.0");
        }
    }

    // ─────────────────────────────────────────
    // fetchAllTransactions
    // ─────────────────────────────────────────

    @Nested
    @DisplayName("fetchAllTransactions")
    class FetchAllTransactionsTests {

        @Test
        @DisplayName("should return paginated list of all transactions")
        void should_return_all_transactions() {
            PaymentTransaction tx = buildTransaction(TransactionStatus.SUCCESS);
            Page<PaymentTransaction> page = new PageImpl<>(List.of(tx));

            when(transactionRepository.findAll(any(Pageable.class))).thenReturn(page);

            Page<PaymentTransaction> result = paymentService.fetchAllTransactions(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getReference()).isEqualTo("REF-001");
        }

        @Test
        @DisplayName("should return empty page when no transactions exist")
        void should_return_empty_page() {
            Page<PaymentTransaction> emptyPage = Page.empty();
            when(transactionRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

            Page<PaymentTransaction> result = paymentService.fetchAllTransactions(0, 10);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should pass correct pageable to repository")
        void should_pass_correct_pageable() {
            when(transactionRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

            paymentService.fetchAllTransactions(2, 5);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(transactionRepository).findAll(captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }
    }

    // fetchUserTransactions

    @Nested
    @DisplayName("fetchUserTransactions")
    class FetchUserTransactionsTests {

        @Test
        @DisplayName("should return paginated transactions for a given email")
        void should_return_transactions_by_email() {
            PaymentTransaction tx = buildTransaction(TransactionStatus.SUCCESS);
            Page<PaymentTransaction> page = new PageImpl<>(List.of(tx));

            when(transactionRepository.findByCustomerEmail(eq("user@example.com"), any(Pageable.class)))
                    .thenReturn(page);

            Page<PaymentTransaction> result =
                    paymentService.fetchUserTransactions("user@example.com", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCustomerEmail()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("should return empty page when user has no transactions")
        void should_return_empty_page_for_unknown_email() {
            when(transactionRepository.findByCustomerEmail(eq("unknown@example.com"), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Page<PaymentTransaction> result =
                    paymentService.fetchUserTransactions("unknown@example.com", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should pass correct pageable to repository")
        void should_pass_correct_pageable() {
            when(transactionRepository.findByCustomerEmail(any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            paymentService.fetchUserTransactions("user@example.com", 1, 20);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(transactionRepository).findByCustomerEmail(any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        }
    }

    // fetchTransactionByReference

    @Nested
    @DisplayName("fetchTransactionByReference")
    class FetchTransactionByReferenceTests {

        @Test
        @DisplayName("should return transaction when found")
        void should_return_transaction_by_reference() {
            PaymentTransaction tx = buildTransaction(TransactionStatus.SUCCESS);
            when(transactionRepository.findByReference("REF-001")).thenReturn(Optional.of(tx));

            PaymentTransaction result = paymentService.fetchTransactionByReference("REF-001");

            assertThat(result).isNotNull();
            assertThat(result.getReference()).isEqualTo("REF-001");
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when reference not found")
        void should_throw_when_reference_not_found() {
            when(transactionRepository.findByReference("UNKNOWN-REF")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.fetchTransactionByReference("UNKNOWN-REF")
            ).isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("UNKNOWN-REF");
        }
    }

    // fetchUserTransactionsByUserId

    @Nested
    @DisplayName("fetchUserTransactionsByUserId")
    class FetchUserTransactionsByUserIdTests {

        @Test
        @DisplayName("should return paginated transactions for a given userId")
        void should_return_transactions_by_userId() {
            PaymentTransaction tx = buildTransaction(TransactionStatus.SUCCESS);
            Page<PaymentTransaction> page = new PageImpl<>(List.of(tx));

            when(transactionRepository.findTransactionsByUserId(eq("1"), any(Pageable.class)))
                    .thenReturn(page);

            Page<PaymentTransaction> result =
                    paymentService.fetchUserTransactionsByUserId("1", null, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return empty page when user has no transactions")
        void should_return_empty_page_when_no_transactions() {
            when(transactionRepository.findTransactionsByUserId(eq("99"), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Page<PaymentTransaction> result =
                    paymentService.fetchUserTransactionsByUserId("99", null, 0, 10);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should pass correct pageable to repository")
        void should_pass_correct_pageable() {
            when(transactionRepository.findTransactionsByUserId(any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            paymentService.fetchUserTransactionsByUserId("1", null, 3, 15);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(transactionRepository).findTransactionsByUserId(any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
            assertThat(captor.getValue().getPageSize()).isEqualTo(15);
        }

        @Test
        @DisplayName("should handle null filter gracefully")
        void should_handle_null_filter() {
            when(transactionRepository.findTransactionsByUserId(any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            assertThatCode(() ->
                    paymentService.fetchUserTransactionsByUserId("1", null, 0, 10)
            ).doesNotThrowAnyException();
        }
    }
}
