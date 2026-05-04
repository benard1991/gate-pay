package com.gatepay.walletservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.walletservice.dto.*;
import com.gatepay.walletservice.enums.*;
import com.gatepay.walletservice.exception.*;
import com.gatepay.walletservice.service.TransactionService;
import com.gatepay.walletservice.service.WalletService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserWalletController.class)
@DisplayName("UserWalletController")
class UserWalletControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean WalletService      walletService;
    @MockBean TransactionService transactionService;


    private WalletResponse walletResponse() {
        return WalletResponse.builder()
                .id(1L)
                .userId(42L)
                .balance(new BigDecimal("1000.00"))
                .ledgerBalance(new BigDecimal("1000.00"))
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private WalletTransactionResponse debitTransactionResponse() {
        return WalletTransactionResponse.builder()
                .id(10L)
                .reference("WLT-DEBIT-001")
                .type(TransactionType.DEBIT)
                .source(TransactionSource.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("300.00"))
                .balanceBefore(new BigDecimal("1000.00"))
                .balanceAfter(new BigDecimal("700.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PageResponse<WalletTransactionResponse> transactionPage() {
        return PageResponse.<WalletTransactionResponse>builder()
                .content(List.of(debitTransactionResponse()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
    }

    // GET /wallets/{userId}

    @Nested
    @DisplayName("GET /wallets/{userId}")
    class GetWallet {

        @Test
        @DisplayName("returns the wallet when found")
        void returnsWalletWhenFound() throws Exception {
            when(walletService.getWalletByUserId(42L)).thenReturn(walletResponse());

            mockMvc.perform(get("/wallets/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(42))
                    .andExpect(jsonPath("$.data.balance").value(1000.00))
                    .andExpect(jsonPath("$.data.currency").value("NGN"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.message").value("Wallet retrieved successfully"));
        }

        @Test
        @DisplayName("returns 404 when wallet does not exist for the given user")
        void returns404WhenWalletNotFound() throws Exception {
            when(walletService.getWalletByUserId(99L))
                    .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

            mockMvc.perform(get("/wallets/99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 403 when wallet is suspended")
        void returns403WhenWalletSuspended() throws Exception {
            when(walletService.getWalletByUserId(42L))
                    .thenThrow(new WalletServiceException(ErrorCode.WALLET_SUSPENDED));

            mockMvc.perform(get("/wallets/42"))
                    .andExpect(status().isForbidden());
        }
    }

    // POST /wallets/debit

    @Nested
    @DisplayName("POST /wallets/debit")
    class DebitWallet {

        @Test
        @DisplayName("debits the wallet and returns 201 with the transaction details")
        void debitsWalletAndReturns201() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("300.00"))
                    .source(TransactionSource.WITHDRAWAL)
                    .idempotencyKey("idem-debit-001")
                    .description("Payment")
                    .build();

            when(transactionService.debit(any(DebitWalletRequest.class)))
                    .thenReturn(debitTransactionResponse());

            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reference").value("WLT-DEBIT-001"))
                    .andExpect(jsonPath("$.data.type").value("DEBIT"))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.amount").value(300.00))
                    .andExpect(jsonPath("$.data.balanceBefore").value(1000.00))
                    .andExpect(jsonPath("$.data.balanceAfter").value(700.00))
                    .andExpect(jsonPath("$.message").value("Wallet debited successfully"));
        }

        @Test
        @DisplayName("returns 400 when amount is missing from the request")
        void returns400WhenAmountIsMissing() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .source(TransactionSource.WITHDRAWAL)
                    .idempotencyKey("idem-debit-001")
                    .build();

            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when amount is zero")
        void returns400WhenAmountIsZero() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .amount(BigDecimal.ZERO)
                    .source(TransactionSource.WITHDRAWAL)
                    .idempotencyKey("idem-debit-001")
                    .build();

            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when idempotency key is missing")
        void returns400WhenIdempotencyKeyIsMissing() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("100.00"))
                    .source(TransactionSource.WITHDRAWAL)
                    .build();

            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 422 when wallet has insufficient balance")
        void returns422WhenInsufficientBalance() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("9999.00"))
                    .source(TransactionSource.WITHDRAWAL)
                    .idempotencyKey("idem-debit-002")
                    .build();

            when(transactionService.debit(any()))
                    .thenThrow(new InsufficientBalanceException(
                            "Insufficient balance. Available: 1000.00 Requested: 9999.00"));

            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("returns 403 when wallet is suspended")
        void returns403WhenWalletIsSuspended() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("100.00"))
                    .source(TransactionSource.WITHDRAWAL)
                    .idempotencyKey("idem-debit-003")
                    .build();

            when(transactionService.debit(any()))
                    .thenThrow(new WalletServiceException(ErrorCode.WALLET_SUSPENDED));

            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 403 when wallet is closed")
        void returns403WhenWalletIsClosed() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("100.00"))
                    .source(TransactionSource.WITHDRAWAL)
                    .idempotencyKey("idem-debit-004")
                    .build();

            when(transactionService.debit(any()))
                    .thenThrow(new WalletServiceException(ErrorCode.WALLET_CLOSED));

            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns the same response on a duplicate idempotency key")
        void returnsCachedResponseOnDuplicateIdempotencyKey() throws Exception {
            DebitWalletRequest request = DebitWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("300.00"))
                    .source(TransactionSource.WITHDRAWAL)
                    .idempotencyKey("idem-debit-001")
                    .build();

            when(transactionService.debit(any(DebitWalletRequest.class)))
                    .thenReturn(debitTransactionResponse());

            // first call
            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reference").value("WLT-DEBIT-001"));

            // second call with same idempotency key — service handles dedup, controller just passes through
            mockMvc.perform(post("/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reference").value("WLT-DEBIT-001"));

            verify(transactionService, times(2)).debit(any());
        }
    }

    // GET /wallets/{userId}/transactions

    @Nested
    @DisplayName("GET /wallets/{userId}/transactions")
    class GetTransactions {

        @Test
        @DisplayName("returns a paged list of transactions for the given user")
        void returnsPaginatedTransactionsForUser() throws Exception {
            when(transactionService.getTransactions(eq(42L), any(TransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/42/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].reference").value("WLT-DEBIT-001"))
                    .andExpect(jsonPath("$.data.content[0].type").value("DEBIT"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.last").value(true))
                    .andExpect(jsonPath("$.message").value("Transactions retrieved successfully"));
        }

        @Test
        @DisplayName("returns an empty page when the user has no transactions")
        void returnsEmptyPageWhenNoTransactions() throws Exception {
            PageResponse<WalletTransactionResponse> emptyPage = PageResponse
                    .<WalletTransactionResponse>builder()
                    .content(List.of())
                    .page(0).size(20).totalElements(0).totalPages(0).last(true)
                    .build();

            when(transactionService.getTransactions(eq(42L), any()))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/wallets/42/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("returns 404 when no wallet exists for the given user")
        void returns404WhenWalletNotFound() throws Exception {
            when(transactionService.getTransactions(eq(99L), any()))
                    .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

            mockMvc.perform(get("/wallets/99/transactions"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("passes type filter to the service when provided")
        void passesTypeFilterToService() throws Exception {
            when(transactionService.getTransactions(eq(42L), any(TransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/42/transactions")
                            .param("type", "DEBIT"))
                    .andExpect(status().isOk());

            verify(transactionService).getTransactions(eq(42L),
                    argThat(f -> f.getType() == TransactionType.DEBIT));
        }

        @Test
        @DisplayName("passes status filter to the service when provided")
        void passesStatusFilterToService() throws Exception {
            when(transactionService.getTransactions(eq(42L), any(TransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/42/transactions")
                            .param("status", "SUCCESS"))
                    .andExpect(status().isOk());

            verify(transactionService).getTransactions(eq(42L),
                    argThat(f -> f.getStatus() == TransactionStatus.SUCCESS));
        }

        @Test
        @DisplayName("passes pagination parameters to the service when provided")
        void passesPaginationToService() throws Exception {
            when(transactionService.getTransactions(eq(42L), any(TransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/42/transactions")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk());

            verify(transactionService).getTransactions(eq(42L),
                    argThat(f -> f.getPage() == 1 && f.getSize() == 10));
        }
    }

    // GET /wallets/transactions/{reference}

    @Nested
    @DisplayName("GET /wallets/transactions/{reference}")
    class GetTransactionByReference {

        @Test
        @DisplayName("returns the transaction when the reference exists")
        void returnsTransactionWhenFound() throws Exception {
            when(transactionService.getByReference("WLT-DEBIT-001"))
                    .thenReturn(debitTransactionResponse());

            mockMvc.perform(get("/wallets/transactions/WLT-DEBIT-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reference").value("WLT-DEBIT-001"))
                    .andExpect(jsonPath("$.data.type").value("DEBIT"))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.amount").value(300.00))
                    .andExpect(jsonPath("$.message").value("Transaction retrieved successfully"));
        }

        @Test
        @DisplayName("returns 404 when the reference does not exist")
        void returns404WhenReferenceNotFound() throws Exception {
            when(transactionService.getByReference("GHOST-REF"))
                    .thenThrow(new TransactionNotFoundException("Transaction not found: GHOST-REF"));

            mockMvc.perform(get("/wallets/transactions/GHOST-REF"))
                    .andExpect(status().isNotFound());
        }
    }
}