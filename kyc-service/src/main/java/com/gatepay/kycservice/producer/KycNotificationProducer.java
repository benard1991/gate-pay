package com.gatepay.kycservice.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.kycservice.config.RabbitMQConfig;
import com.gatepay.kycservice.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class KycNotificationProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    private static final String EXCHANGE     = RabbitMQConfig.NOTIFICATION_EXCHANGE;
    private static final String ROUTING_KEY  = RabbitMQConfig.NOTIFICATION_ROUTING_KEY;


    public void sendKycNotification(NotificationMessage message) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
    }

    // Used by the outbox relay. Waits for the broker ack and throws if the
    // message wasn't accepted, so the relay knows to retry it.
    public void publishConfirmed(String exchange, String routingKey, String json) throws Exception {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, json, correlation);

        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("Broker NACKed message: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            // acked but no queue took it
            throw new IllegalStateException("Message unroutable: exchange=" + exchange
                    + " routingKey=" + routingKey
                    + " reply=" + correlation.getReturned().getReplyText());
        }
    }
}