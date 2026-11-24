package com.gatepay.walletservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.walletservice.dto.CreateWalletRequest;
import com.gatepay.walletservice.exception.WalletAlreadyExistsException;
import com.gatepay.walletservice.service.WalletService;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KycEventListener {

    private final WalletService walletService;
    private final ObjectMapper  objectMapper;

    @RabbitListener(queues = "${kyc.rabbitmq.queue.approved:kyc.approved.queue}")
    public void onKycApproved(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            Long userId = Long.valueOf(payload.get("userId").toString());

            if (walletService.walletExists(userId)) {
                log.info("Wallet already exists for userId: {}", userId);
                return;
            }

            try {
                walletService.createWallet(CreateWalletRequest.builder()
                        .userId(userId)
                        .currency("NGN")
                        .build());
                log.info("Wallet auto-created for KYC approved userId: {}", userId);
            } catch (WalletAlreadyExistsException | DataIntegrityViolationException e) {
                // the same event got here twice at the same time and the other copy won;
                // the wallet exists, which is all we wanted
                log.info("Wallet already created by a duplicate event for userId: {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to process KYC approved event: {}", e.getMessage());
            throw new RuntimeException("Failed to process KYC event", e);
        }
    }
}
