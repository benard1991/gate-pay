package com.gatepay.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@DisplayName("AdminController Unit Tests")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private PaymentTransaction transaction1;
    private PaymentTransaction transaction2;

    @BeforeEach
    void setUp() {
        transaction1 = PaymentTransaction.builder()
                .id(1L)
                .reference("REF-001")
                .provider(PaymentProvider.PAYSTACK)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("5000.00"))
                .currency("NGN")
                .userId(101L)
                .customerEmail("john@example.com")
                .customerName("John Doe")
                .phoneNumber("08012345678")
                .createdAt(LocalDateTime.now())
                .build();

        transaction2 = PaymentTransaction.builder()
                .id(2L)
                .reference("REF-002")
                .provider(PaymentProvider.FLUTTERWAVE)
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("10000.00"))
                .currency("NGN")
                .userId(102L)
                .customerEmail("jane@example.com")
                .customerName("Jane Doe")
                .phoneNumber("08098765432")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /payments/admin/transactions
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /payments/admin/transactions")
    class FetchAllTransactions {

        @Test
        @DisplayName("should return 200 with paginated transactions using default page and size")
        void should_return_paginated_transactions_with_defaults() throws Exception {
            Page<PaymentTransaction> page = new PageImpl<>(
                    List.of(transaction1, transaction2),
                    PageRequest.of(0, 10),
                    2
            );
            when(paymentService.fetchAllTransactions(0, 10)).thenReturn(page);

            mockMvc.perform(get("/payments/admin/transactions")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("Transactions fetched successfully"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.pageSize").value(10))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.content[0].reference").value("REF-001"))
                    .andExpect(jsonPath("$.data.content[1].reference").value("REF-002"));

            verify(paymentService).fetchAllTransactions(0, 10);
        }

        @Test
        @DisplayName("should return 200 with custom page and size params")
        void should_return_paginated_transactions_with_custom_params() throws Exception {
            Page<PaymentTransaction> page = new PageImpl<>(
                    List.of(transaction1),
                    PageRequest.of(1, 5),
                    6
            );
            when(paymentService.fetchAllTransactions(1, 5)).thenReturn(page);

            mockMvc.perform(get("/payments/admin/transactions")
                            .param("page", "1")
                            .param("size", "5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.pageSize").value(5))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(6))
                    .andExpect(jsonPath("$.data.content.length()").value(1));

            verify(paymentService).fetchAllTransactions(1, 5);
        }

        @Test
        @DisplayName("should return 200 with empty content when no transactions exist")
        void should_return_empty_content_when_no_transactions() throws Exception {
            Page<PaymentTransaction> emptyPage = new PageImpl<>(
                    List.of(),
                    PageRequest.of(0, 10),
                    0
            );
            when(paymentService.fetchAllTransactions(0, 10)).thenReturn(emptyPage);

            mockMvc.perform(get("/payments/admin/transactions")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.totalPages").value(0));
        }

        @Test
        @DisplayName("should call paymentService with correct page and size arguments")
        void should_delegate_to_service_with_correct_args() throws Exception {
            Page<PaymentTransaction> page = new PageImpl<>(List.of(), PageRequest.of(2, 15), 0);
            when(paymentService.fetchAllTransactions(2, 15)).thenReturn(page);

            mockMvc.perform(get("/payments/admin/transactions")
                            .param("page", "2")
                            .param("size", "15"))
                    .andExpect(status().isOk());

            verify(paymentService, times(1)).fetchAllTransactions(2, 15);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GET /payments/admin/{email}/transactions
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /payments/admin/{email}/transactions")
    class FetchUserTransactions {

        @Test
        @DisplayName("should return 200 with user transactions using default page and size")
        void should_return_user_transactions_with_defaults() throws Exception {
            Page<PaymentTransaction> page = new PageImpl<>(
                    List.of(transaction1),
                    PageRequest.of(0, 20),
                    1
            );
            when(paymentService.fetchUserTransactions("john@example.com", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/payments/admin/{email}/transactions", "john@example.com")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User transactions fetched successfully"))
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].customerEmail").value("john@example.com"))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.pageSize").value(20))
                    .andExpect(jsonPath("$.data.totalElements").value(1));

            verify(paymentService).fetchUserTransactions("john@example.com", 0, 20);
        }

        @Test
        @DisplayName("should return 200 with custom page and size for user transactions")
        void should_return_user_transactions_with_custom_params() throws Exception {
            Page<PaymentTransaction> page = new PageImpl<>(
                    List.of(transaction1, transaction2),
                    PageRequest.of(0, 50),
                    2
            );
            when(paymentService.fetchUserTransactions("john@example.com", 0, 50)).thenReturn(page);

            mockMvc.perform(get("/payments/admin/{email}/transactions", "john@example.com")
                            .param("page", "0")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageSize").value(50))
                    .andExpect(jsonPath("$.data.content.length()").value(2));

            verify(paymentService).fetchUserTransactions("john@example.com", 0, 50);
        }

        @Test
        @DisplayName("should return 200 with empty list when user has no transactions")
        void should_return_empty_when_user_has_no_transactions() throws Exception {
            Page<PaymentTransaction> emptyPage = new PageImpl<>(
                    List.of(),
                    PageRequest.of(0, 20),
                    0
            );
            when(paymentService.fetchUserTransactions("nobody@example.com", 0, 20)).thenReturn(emptyPage);

            mockMvc.perform(get("/payments/admin/{email}/transactions", "nobody@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("should pass the email path variable correctly to the service")
        void should_pass_email_to_service_correctly() throws Exception {
            Page<PaymentTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(paymentService.fetchUserTransactions(anyString(), anyInt(), anyInt())).thenReturn(page);

            mockMvc.perform(get("/payments/admin/{email}/transactions", "test@gatepay.com"))
                    .andExpect(status().isOk());

            verify(paymentService).fetchUserTransactions("test@gatepay.com", 0, 20);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GET /payments/admin/{reference}
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /payments/admin/{reference}")
    class FetchByReference {

        @Test
        @DisplayName("should return 200 with transaction when reference exists")
        void should_return_transaction_by_reference() throws Exception {
            when(paymentService.fetchTransactionByReference("REF-001")).thenReturn(transaction1);

            mockMvc.perform(get("/payments/admin/{reference}", "REF-001")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("Transaction fetched successfully"))
                    .andExpect(jsonPath("$.data.reference").value("REF-001"))
                    .andExpect(jsonPath("$.data.provider").value("PAYSTACK"))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.amount").value(5000.00))
                    .andExpect(jsonPath("$.data.customerEmail").value("john@example.com"));

            verify(paymentService).fetchTransactionByReference("REF-001");
        }

        @Test
        @DisplayName("should return correct transaction fields for Flutterwave transaction")
        void should_return_flutterwave_transaction_by_reference() throws Exception {
            when(paymentService.fetchTransactionByReference("REF-002")).thenReturn(transaction2);

            mockMvc.perform(get("/payments/admin/{reference}", "REF-002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reference").value("REF-002"))
                    .andExpect(jsonPath("$.data.provider").value("FLUTTERWAVE"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.amount").value(10000.00));

            verify(paymentService).fetchTransactionByReference("REF-002");
        }

        @Test
        @DisplayName("should call paymentService with the exact reference from the path")
        void should_delegate_reference_to_service() throws Exception {
            when(paymentService.fetchTransactionByReference("TXN-XYZ-999")).thenReturn(transaction1);

            mockMvc.perform(get("/payments/admin/{reference}", "TXN-XYZ-999"))
                    .andExpect(status().isOk());

            verify(paymentService, times(1)).fetchTransactionByReference("TXN-XYZ-999");
        }
    }
}