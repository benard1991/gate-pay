package com.gatepay.paymentservice.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    public void sendPaymentSuccessEvent(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PAYMENT_SUCCESS_EXCHANGE,
                    RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY,
                    json
            );
            log.info("Payment success event published | ref={}",
                    payload.get("reference"));
        } catch (Exception e) {
            log.error("Failed to publish payment success event | ref={} | error={}",
                    payload.get("reference"), e.getMessage());
        }
    }
}