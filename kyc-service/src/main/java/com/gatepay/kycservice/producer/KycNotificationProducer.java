package com.gatepay.kycservice.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.kycservice.config.RabbitMQConfig;
import com.gatepay.kycservice.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycNotificationProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    private static final String EXCHANGE     = RabbitMQConfig.NOTIFICATION_EXCHANGE;
    private static final String ROUTING_KEY  = RabbitMQConfig.NOTIFICATION_ROUTING_KEY;

    private static final String KYC_APPROVED_EXCHANGE     = RabbitMQConfig.KYC_APPROVED_EXCHANGE;
    private static final String KYC_APPROVED_ROUTING_KEY  = RabbitMQConfig.KYC_APPROVED_ROUTING_KEY;

    public void sendKycNotification(NotificationMessage message) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
    }

    public void sendKycApprovedEvent(Long userId) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "userId", userId,
                    "event",  "KYC_APPROVED"
            ));
            rabbitTemplate.convertAndSend(KYC_APPROVED_EXCHANGE, KYC_APPROVED_ROUTING_KEY, json);
            log.info("KYC approved event published for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish KYC approved event for userId: {} error: {}", userId, e.getMessage());
        }
    }
}