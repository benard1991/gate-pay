package com.gatepay.paymentservice.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

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

    // Used by the outbox relay. Waits for the broker ack and throws if the
    // message wasn't accepted, so the relay knows to retry it.
    public void publishConfirmed(String exchange, String routingKey, String json) throws Exception {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, json, correlation);

        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!confirm.isAck()) {
            throw new IllegalStateException("Broker NACKed message: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            // acked but no queue picked it up
            throw new IllegalStateException("Message unroutable: exchange=" + exchange
                    + " routingKey=" + routingKey
                    + " reply=" + correlation.getReturned().getReplyText());
        }
    }
}