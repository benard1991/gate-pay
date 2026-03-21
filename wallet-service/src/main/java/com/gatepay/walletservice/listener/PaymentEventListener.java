package com.gatepay.walletservice.listener;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.walletservice.dto.CreditWalletRequest;
import com.gatepay.walletservice.enums.TransactionSource;
import com.gatepay.walletservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final TransactionService transactionService;
    private final ObjectMapper       objectMapper;

    @RabbitListener(queues = "${payment.rabbitmq.queue.success:payment.success.queue}")
    public void onPaymentSuccess(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);

            Long userId       = Long.valueOf(payload.get("userId").toString());
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            String reference  = payload.get("reference").toString();

            CreditWalletRequest request = CreditWalletRequest.builder()
                    .userId(userId)
                    .amount(amount)
                    .source(TransactionSource.TOPUP)
                    .idempotencyKey(reference)
                    .description("Wallet top-up via payment: " + reference)
                    .build();

            transactionService.credit(request);
            log.info("Wallet credited from payment | userId={} amount={} ref={}",
                    userId, amount, reference);

        } catch (Exception e) {
            log.error("Failed to process payment success event: {}", e.getMessage());
            throw new RuntimeException("Failed to process payment event", e);
        }
    }
}