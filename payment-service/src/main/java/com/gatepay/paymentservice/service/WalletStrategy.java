//package com.gatepay.paymentservice.service;
//
//import com.gatepay.paymentservice.dto.PaymentRequest;
//import com.gatepay.paymentservice.dto.PaymentResponse;
//import com.gatepay.paymentservice.dto.VerifyPaymentResponse;
//import com.gatepay.paymentservice.model.Wallet;
//import com.gatepay.paymentservice.model.enums.TransactionStatus;
//import com.gatepay.paymentservice.repository.WalletRepository;
//import com.gatepay.paymentservice.service.PaymentStrategy;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Component
//@Slf4j
//@RequiredArgsConstructor
//public class WalletStrategy implements PaymentStrategy {
//
//    private final WalletRepository walletRepository;
//
//    private static final List<String> SUPPORTED_CURRENCIES = List.of("NGN", "USD", "GHS");
//
//    @Override
//    @Transactional
//    public PaymentResponse initiatePayment(PaymentRequest request) {
//        log.info("Initiating Wallet payment for reference: {}", request.getReference());
//
//        try {
//            // Find wallet by email or customer identifier
//            Wallet wallet = walletRepository.findByEmail(request.getEmail())
//                    .orElseThrow(() -> new RuntimeException("Wallet not found"));
//
//            // Check if wallet has sufficient balance
//            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
//                return PaymentResponse.builder()
//                        .success(false)
//                        .message("Insufficient wallet balance")
//                        .reference(request.getReference())
//                        .provider("WALLET")
//                        .build();
//            }
//
//            // Deduct amount from wallet
//            BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
//            wallet.setBalance(newBalance);
//            walletRepository.save(wallet);
//
//            log.info("Wallet payment successful. New balance: {}", newBalance);
//
//            return PaymentResponse.builder()
//                    .success(true)
//                    .message("Payment successful")
//                    .reference(request.getReference())
//                    .provider("WALLET")
//                    .build();
//
//        } catch (Exception e) {
//            log.error("Error processing wallet payment: {}", e.getMessage(), e);
//            return PaymentResponse.builder()
//                    .success(false)
//                    .message("Error: " + e.getMessage())
//                    .reference(request.getReference())
//                    .provider("WALLET")
//                    .build();
//        }
//    }
//
//    @Override
//    public VerifyPaymentResponse verifyPayment(String reference) {
//        log.info("Verifying Wallet payment for reference: {}", reference);
//
//        // For wallet payments, verification is instant
//        // Status is SUCCESS since wallet payments are processed immediately
//
//
//        return VerifyPaymentResponse.builder()
//                .success(true)
//                .status(String.valueOf(TransactionStatus.SUCCESS))
//                .reference(reference)
//                .provider("WALLET")
//                .paidAt(LocalDateTime.now())
//                .build();
//    }
//
//    @Override
//    public String getProviderName() {
//        return "WALLET";
//    }
//
//    @Override
//    public boolean supportsCurrency(String currency) {
//        return SUPPORTED_CURRENCIES.contains(currency.toUpperCase());
//    }
//}