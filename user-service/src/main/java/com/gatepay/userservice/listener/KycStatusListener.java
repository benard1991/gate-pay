package com.gatepay.userservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.userservice.config.RabbitMQConfig;
import com.gatepay.userservice.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

// Keeps kycVerified in sync with KYC decisions made in kyc-service.
// Events can arrive more than once (outbox is at-least-once), which is fine here
// because setting the same value twice doesn't change anything.
@Slf4j
@Component
@RequiredArgsConstructor
public class KycStatusListener {

    private final AdminService adminService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.KYC_STATUS_QUEUE)
    public void onKycStatusChanged(String message) throws Exception {
        Map<?, ?> payload = objectMapper.readValue(message, Map.class);
        Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
        String event = String.valueOf(payload.get("event"));

        boolean verified;
        switch (event) {
            case "KYC_APPROVED" -> verified = true;
            case "KYC_REJECTED" -> verified = false;
            default -> {
                log.warn("Ignoring unknown KYC event {} for userId={}", event, userId);
                return;
            }
        }

        adminService.updateKycVerified(userId, verified);
        log.info("kycVerified={} for userId={} ({})", verified, userId, event);
    }
}
