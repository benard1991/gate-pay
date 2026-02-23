package com.gatepay.kycservice.producer;

import com.gatepay.kycservice.config.RabbitMQConfig;
import com.gatepay.kycservice.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class KycNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = RabbitMQConfig.NOTIFICATION_EXCHANGE;
    private static final String ROUTING_KEY = RabbitMQConfig.NOTIFICATION_ROUTING_KEY;

    public void sendKycNotification(NotificationMessage message) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
    }
}

