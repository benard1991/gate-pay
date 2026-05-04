package com.gatepay.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.dto.*;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController Unit Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private PaymentRequest validPaymentRequest;
    private PaymentResponse successPaymentResponse;
    private PaymentResponse failedPaymentResponse;
    private VerifyPaymentRequest verifyPaymentRequest;
    private VerifyPaymentResponse successVerifyResponse;
    private VerifyPaymentResponse failedVerifyResponse;

    @BeforeEach
    void setUp() {
        validPaymentRequest = PaymentRequest.builder()
                .userId(1L)
                .walletId(10L)
                .email("john@example.com")
                .amount(new BigDecimal("5000.00"))
                .currency("NGN")
                .callbackUrl("https://example.com/callback")
                .customerName("John Doe")
                .phoneNumber("08012345678")
                .build();

        successPaymentResponse = PaymentResponse.builder()
                .success(true)
                .message("Payment initialized successfully")
                .reference("REF-001")
                .authorizationUrl("https://paystack.com/pay/abc")
                .accessCode("ACCESS-001")
                .provider("PAYSTACK")
                .build();

        failedPaymentResponse = PaymentResponse.builder()
                .success(false)
                .message("Payment initialization failed")
                .provider("PAYSTACK")
                .build();

        verifyPaymentRequest = new VerifyPaymentRequest();
        verifyPaymentRequest.setReference("REF-001");
        verifyPaymentRequest.setProvider(PaymentProvider.PAYSTACK);

        successVerifyResponse = VerifyPaymentResponse.builder()
                .success(true)
                .message("Verification successful")
                .provider("PAYSTACK")
                .data(VerifyPaymentResponse.TransactionData.builder()
                        .reference("REF-001")
                        .status("success")
                        .amount(new BigDecimal("5000.00"))
                        .currency("NGN")
                        .build())
                .build();

        failedVerifyResponse = VerifyPaymentResponse.builder()
                .success(false)
                .message("Verification failed")
                .provider("PAYSTACK")
                .build();
    }

    // INITIALIZE PAYMENT

    @Nested
    class InitializePayment {

        @Test
        void should_return_200_when_successful() throws Exception {
            when(paymentService.initializePayment(any(), any(), any()))
                    .thenReturn(successPaymentResponse);

            mockMvc.perform(post("/payments/initializePayment")
                            .param("provider", "PAYSTACK")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void should_return_400_when_failed() throws Exception {
            when(paymentService.initializePayment(any(), any(), any()))
                    .thenReturn(failedPaymentResponse);

            mockMvc.perform(post("/payments/initializePayment")
                            .param("provider", "PAYSTACK")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void should_extract_ip_from_header() throws Exception {
            when(paymentService.initializePayment(any(), any(), eq("192.168.1.1")))
                    .thenReturn(successPaymentResponse);

            mockMvc.perform(post("/payments/initializePayment")
                            .param("provider", "PAYSTACK")
                            .header("X-Forwarded-For", "192.168.1.1, 10.0.0.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk());
        }
    }

    // VERIFY PAYMENT

    @Nested
    class VerifyPayment {

        @Test
        void should_return_200_when_successful() throws Exception {
            when(paymentService.verifyPayment(any(), any(), any(), any()))
                    .thenReturn(successVerifyResponse);

            mockMvc.perform(post("/payments/verifyPayment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(verifyPaymentRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(true));
        }

        @Test
        void should_return_400_when_failed() throws Exception {
            when(paymentService.verifyPayment(any(), any(), any(), any()))
                    .thenReturn(failedVerifyResponse);

            mockMvc.perform(post("/payments/verifyPayment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(verifyPaymentRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(false));
        }

        @Test
        void should_return_422_when_reference_missing() throws Exception {
            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setProvider(PaymentProvider.PAYSTACK);

            mockMvc.perform(post("/payments/verifyPayment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void should_return_422_when_provider_missing() throws Exception {
            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setReference("REF");

            mockMvc.perform(post("/payments/verifyPayment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // FETCH USER TRANSACTIONS

    @Nested
    class FetchUserTransactions {

        @Test
        void should_return_paginated_result() throws Exception {
            PaymentTransaction tx = PaymentTransaction.builder()
                    .reference("REF-001")
                    .status(TransactionStatus.SUCCESS)
                    .build();

            Page<PaymentTransaction> page =
                    new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1);

            when(paymentService.fetchUserTransactionsByUserId(any(), any(), anyInt(), anyInt()))
                    .thenReturn(page);

            mockMvc.perform(get("/payments/101/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1));
        }
    }
}