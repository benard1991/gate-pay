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

@WebMvcTest(AdminWalletController.class)
@DisplayName("AdminWalletController")
class AdminWalletControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean WalletService      walletService;
    @MockBean TransactionService transactionService;


    private WalletResponse walletResponse() {
        return WalletResponse.builder()
                .id(1L)
                .userId(42L)
                .balance(new BigDecimal("1000.00"))
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .build();
    }

    private WalletTransactionResponse transactionResponse() {
        return WalletTransactionResponse.builder()
                .id(99L)
                .reference("WLT-ABC123")
                .type(TransactionType.CREDIT)
                .source(TransactionSource.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("200.00"))
                .balanceBefore(new BigDecimal("800.00"))
                .balanceAfter(new BigDecimal("1000.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PageResponse<WalletTransactionResponse> transactionPage() {
        return PageResponse.<WalletTransactionResponse>builder()
                .content(List.of(transactionResponse()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
    }

    private PageResponse<WalletResponse> walletPage() {
        return PageResponse.<WalletResponse>builder()
                .content(List.of(walletResponse()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
    }

    // =========================================================================
    // POST /wallets/admin — create wallet
    // =========================================================================

    @Nested
    @DisplayName("POST /wallets/admin")
    class CreateWallet {

        @Test
        @DisplayName("creates a wallet and returns 201 with the wallet details")
        void createsWalletAndReturns201() throws Exception {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(42L)
                    .currency("NGN")
                    .build();

            when(walletService.createWallet(any(CreateWalletRequest.class)))
                    .thenReturn(walletResponse());

            mockMvc.perform(post("/wallets/admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.userId").value(42))
                    .andExpect(jsonPath("$.data.currency").value("NGN"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.message").value("Wallet created successfully"));
        }

        @Test
        @DisplayName("returns 400 when userId is missing from the request")
        void returns400WhenUserIdMissing() throws Exception {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .currency("NGN")
                    .build();

            mockMvc.perform(post("/wallets/admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 409 when wallet already exists for that user")
        void returns409WhenWalletAlreadyExists() throws Exception {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(42L)
                    .currency("NGN")
                    .build();

            when(walletService.createWallet(any()))
                    .thenThrow(new WalletServiceException(ErrorCode.WALLET_ALREADY_EXISTS));

            mockMvc.perform(post("/wallets/admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    // =========================================================================
    // GET /wallets/admin/{userId} — get wallet by userId
    // =========================================================================

    @Nested
    @DisplayName("GET /wallets/admin/{userId}")
    class GetWallet {

        @Test
        @DisplayName("returns the wallet when the user exists")
        void returnsWalletForExistingUser() throws Exception {
            when(walletService.getWalletByUserId(42L)).thenReturn(walletResponse());

            mockMvc.perform(get("/wallets/admin/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(42))
                    .andExpect(jsonPath("$.data.balance").value(1000.00))
                    .andExpect(jsonPath("$.message").value("Wallet retrieved successfully"));
        }

        @Test
        @DisplayName("returns 404 when no wallet exists for the given userId")
        void returns404WhenWalletNotFound() throws Exception {
            when(walletService.getWalletByUserId(99L))
                    .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

            mockMvc.perform(get("/wallets/admin/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // POST /wallets/admin/credit — credit wallet
    // =========================================================================

    @Nested
    @DisplayName("POST /wallets/admin/credit")
    class CreditWallet {

        @Test
        @DisplayName("credits the wallet and returns 201 with the transaction details")
        void creditsWalletAndReturns201() throws Exception {
            CreditWalletRequest request = CreditWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("200.00"))
                    .source(TransactionSource.TRANSFER)
                    .idempotencyKey("idem-001")
                    .build();

            when(transactionService.credit(any(CreditWalletRequest.class)))
                    .thenReturn(transactionResponse());

            mockMvc.perform(post("/wallets/admin/credit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reference").value("WLT-ABC123"))
                    .andExpect(jsonPath("$.data.type").value("CREDIT"))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.amount").value(200.00))
                    .andExpect(jsonPath("$.message").value("Wallet credited successfully"));
        }

        @Test
        @DisplayName("returns 400 when amount is zero")
        void returns400WhenAmountIsZero() throws Exception {
            CreditWalletRequest request = CreditWalletRequest.builder()
                    .userId(42L)
                    .amount(BigDecimal.ZERO)
                    .source(TransactionSource.TRANSFER)
                    .idempotencyKey("idem-001")
                    .build();

            mockMvc.perform(post("/wallets/admin/credit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 403 when wallet is suspended")
        void returns403WhenWalletIsSuspended() throws Exception {
            CreditWalletRequest request = CreditWalletRequest.builder()
                    .userId(42L)
                    .amount(new BigDecimal("100.00"))
                    .source(TransactionSource.TRANSFER)
                    .idempotencyKey("idem-002")
                    .build();

            when(transactionService.credit(any()))
                    .thenThrow(new WalletServiceException(ErrorCode.WALLET_SUSPENDED));

            mockMvc.perform(post("/wallets/admin/credit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // POST /wallets/admin/reverse/{reference} — reverse transaction
    // =========================================================================

    @Nested
    @DisplayName("POST /wallets/admin/reverse/{reference}")
    class ReverseTransaction {

        @Test
        @DisplayName("reverses the transaction and returns the reversal details")
        void reversesTransactionSuccessfully() throws Exception {
            WalletTransactionResponse reversal = WalletTransactionResponse.builder()
                    .id(99L)
                    .reference("WLT-REVERSAL-001")
                    .type(TransactionType.DEBIT)
                    .source(TransactionSource.REVERSAL)
                    .status(TransactionStatus.SUCCESS)
                    .amount(new BigDecimal("200.00"))
                    .balanceBefore(new BigDecimal("1000.00"))
                    .balanceAfter(new BigDecimal("800.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionService.reverse("WLT-ABC123")).thenReturn(reversal);

            mockMvc.perform(post("/wallets/admin/reverse/WLT-ABC123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reference").value("WLT-REVERSAL-001"))
                    .andExpect(jsonPath("$.data.type").value("DEBIT"))
                    .andExpect(jsonPath("$.message").value("Transaction reversed successfully"));
        }

        @Test
        @DisplayName("returns 404 when the transaction reference does not exist")
        void returns404WhenReferenceNotFound() throws Exception {
            when(transactionService.reverse("GHOST-REF"))
                    .thenThrow(new TransactionNotFoundException("Transaction not found: GHOST-REF"));

            mockMvc.perform(post("/wallets/admin/reverse/GHOST-REF"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 409 when the transaction has already been reversed")
        void returns409WhenAlreadyReversed() throws Exception {
            when(transactionService.reverse("WLT-ABC123"))
                    .thenThrow(new WalletServiceException(ErrorCode.TRANSACTION_ALREADY_REVERSED));

            mockMvc.perform(post("/wallets/admin/reverse/WLT-ABC123"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("returns 422 when the transaction is not in a reversible state")
        void returns422WhenTransactionNotReversible() throws Exception {
            when(transactionService.reverse("WLT-FAILED-001"))
                    .thenThrow(new WalletServiceException(ErrorCode.TRANSACTION_NOT_REVERSIBLE));

            mockMvc.perform(post("/wallets/admin/reverse/WLT-FAILED-001"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // =========================================================================
    // PATCH /wallets/admin/{userId}/suspend — suspend wallet
    // =========================================================================

    @Nested
    @DisplayName("PATCH /wallets/admin/{userId}/suspend")
    class SuspendWallet {

        @Test
        @DisplayName("suspends the wallet and returns the updated wallet")
        void suspendsWalletSuccessfully() throws Exception {
            WalletResponse suspended = WalletResponse.builder()
                    .id(1L)
                    .userId(42L)
                    .balance(new BigDecimal("1000.00"))
                    .currency("NGN")
                    .status(WalletStatus.SUSPENDED)
                    .build();

            when(walletService.suspendWallet(42L)).thenReturn(suspended);

            mockMvc.perform(patch("/wallets/admin/42/suspend"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                    .andExpect(jsonPath("$.message").value("Wallet suspended successfully"));
        }

        @Test
        @DisplayName("returns 404 when wallet does not exist")
        void returns404WhenWalletNotFound() throws Exception {
            when(walletService.suspendWallet(99L))
                    .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

            mockMvc.perform(patch("/wallets/admin/99/suspend"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // PATCH /wallets/admin/{userId}/reactivate — reactivate wallet
    // =========================================================================

    @Nested
    @DisplayName("PATCH /wallets/admin/{userId}/reactivate")
    class ReactivateWallet {

        @Test
        @DisplayName("reactivates a suspended wallet and returns the updated wallet")
        void reactivatesWalletSuccessfully() throws Exception {
            when(walletService.reactivateWallet(42L)).thenReturn(walletResponse());

            mockMvc.perform(patch("/wallets/admin/42/reactivate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.message").value("Wallet reactivated successfully"));
        }

        @Test
        @DisplayName("returns 404 when wallet does not exist")
        void returns404WhenWalletNotFound() throws Exception {
            when(walletService.reactivateWallet(99L))
                    .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

            mockMvc.perform(patch("/wallets/admin/99/reactivate"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // PATCH /wallets/admin/{userId}/close — close wallet
    // =========================================================================

    @Nested
    @DisplayName("PATCH /wallets/admin/{userId}/close")
    class CloseWallet {

        @Test
        @DisplayName("closes the wallet and returns the updated wallet")
        void closesWalletSuccessfully() throws Exception {
            WalletResponse closed = WalletResponse.builder()
                    .id(1L)
                    .userId(42L)
                    .balance(new BigDecimal("1000.00"))
                    .currency("NGN")
                    .status(WalletStatus.CLOSED)
                    .build();

            when(walletService.closeWallet(42L)).thenReturn(closed);

            mockMvc.perform(patch("/wallets/admin/42/close"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"))
                    .andExpect(jsonPath("$.message").value("Wallet closed successfully"));
        }

        @Test
        @DisplayName("returns 404 when wallet does not exist")
        void returns404WhenWalletNotFound() throws Exception {
            when(walletService.closeWallet(99L))
                    .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

            mockMvc.perform(patch("/wallets/admin/99/close"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // GET /wallets/admin/{userId}/transactions — get transactions for a user
    // =========================================================================

    @Nested
    @DisplayName("GET /wallets/admin/{userId}/transactions")
    class GetTransactionsForUser {

        @Test
        @DisplayName("returns a paged list of transactions for the given user")
        void returnsPaginatedTransactionsForUser() throws Exception {
            when(transactionService.getTransactions(eq(42L), any(TransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/admin/42/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].reference").value("WLT-ABC123"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.message").value("Transactions retrieved successfully"));
        }

        @Test
        @DisplayName("returns 404 when no wallet exists for the given user")
        void returns404WhenWalletNotFound() throws Exception {
            when(transactionService.getTransactions(eq(99L), any()))
                    .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

            mockMvc.perform(get("/wallets/admin/99/transactions"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("filters transactions by type when type query param is provided")
        void filtersTransactionsByType() throws Exception {
            when(transactionService.getTransactions(eq(42L), any(TransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/admin/42/transactions")
                            .param("type", "CREDIT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].type").value("CREDIT"));
        }
    }

    // =========================================================================
    // GET /wallets/admin/all — get all wallets (admin)
    // =========================================================================

    @Nested
    @DisplayName("GET /wallets/admin/all")
    class GetAllWallets {

        @Test
        @DisplayName("returns a paged list of all wallets with default pagination")
        void returnsAllWalletsWithDefaultPagination() throws Exception {
            when(walletService.getAllWallets(any(WalletFilterRequest.class)))
                    .thenReturn(walletPage());

            mockMvc.perform(get("/wallets/admin/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].userId").value(42))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.message").value("Wallets retrieved successfully"));
        }

        @Test
        @DisplayName("filters wallets by status when status query param is provided")
        void filtersWalletsByStatus() throws Exception {
            when(walletService.getAllWallets(any(WalletFilterRequest.class)))
                    .thenReturn(walletPage());

            mockMvc.perform(get("/wallets/admin/all")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk());

            verify(walletService).getAllWallets(argThat(f ->
                    f.getStatus() == WalletStatus.ACTIVE
            ));
        }

        @Test
        @DisplayName("passes custom pagination parameters to the service")
        void passesCustomPaginationToService() throws Exception {
            when(walletService.getAllWallets(any(WalletFilterRequest.class)))
                    .thenReturn(walletPage());

            mockMvc.perform(get("/wallets/admin/all")
                            .param("page", "2")
                            .param("size", "10")
                            .param("sortBy", "balance")
                            .param("sortDirection", "asc"))
                    .andExpect(status().isOk());

            verify(walletService).getAllWallets(argThat(f ->
                    f.getPage() == 2 &&
                            f.getSize() == 10 &&
                            f.getSortBy().equals("balance") &&
                            f.getSortDirection().equals("asc")
            ));
        }
    }

    // =========================================================================
    // GET /wallets/admin/transactions — get all transactions (admin)
    // =========================================================================

    @Nested
    @DisplayName("GET /wallets/admin/transactions")
    class GetAllTransactions {

        @Test
        @DisplayName("returns a paged list of all transactions with default pagination")
        void returnsAllTransactionsWithDefaultPagination() throws Exception {
            when(transactionService.getAllTransactions(any(AdminTransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/admin/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].reference").value("WLT-ABC123"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.message").value("Transactions retrieved successfully"));
        }

        @Test
        @DisplayName("filters by userId when userId query param is provided")
        void filtersByUserId() throws Exception {
            when(transactionService.getAllTransactions(any(AdminTransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/admin/transactions")
                            .param("userId", "42"))
                    .andExpect(status().isOk());

            verify(transactionService).getAllTransactions(argThat(f ->
                    f.getUserId().equals(42L)
            ));
        }

        @Test
        @DisplayName("filters by transaction type and status when both are provided")
        void filtersByTypeAndStatus() throws Exception {
            when(transactionService.getAllTransactions(any(AdminTransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/admin/transactions")
                            .param("type", "CREDIT")
                            .param("status", "SUCCESS"))
                    .andExpect(status().isOk());

            verify(transactionService).getAllTransactions(argThat(f ->
                    f.getType() == TransactionType.CREDIT &&
                            f.getStatus() == TransactionStatus.SUCCESS
            ));
        }

        @Test
        @DisplayName("passes date range parameters to the service when provided")
        void passesDateRangeToService() throws Exception {
            when(transactionService.getAllTransactions(any(AdminTransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/admin/transactions")
                            .param("from", "2026-01-01T00:00:00")
                            .param("to",   "2026-12-31T23:59:59"))
                    .andExpect(status().isOk());

            verify(transactionService).getAllTransactions(argThat(f ->
                    f.getFrom() != null && f.getTo() != null
            ));
        }

        @Test
        @DisplayName("passes custom sort parameters to the service")
        void passesCustomSortToService() throws Exception {
            when(transactionService.getAllTransactions(any(AdminTransactionFilterRequest.class)))
                    .thenReturn(transactionPage());

            mockMvc.perform(get("/wallets/admin/transactions")
                            .param("sortBy", "amount")
                            .param("sortDirection", "asc"))
                    .andExpect(status().isOk());

            verify(transactionService).getAllTransactions(argThat(f ->
                    f.getSortBy().equals("amount") &&
                            f.getSortDirection().equals("asc")
            ));
        }
    }
}