package com.gatepay.paymentservice.producer;

import com.gatepay.paymentservice.config.RabbitMQConfig;
import com.gatepay.paymentservice.dto.eventDto.PaymentFailedEventDTO;
import com.gatepay.paymentservice.dto.eventDto.PaymentInitializedEventDTO;
import com.gatepay.paymentservice.dto.eventDto.PaymentSuccessEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish payment success event
     */
    public void publishPaymentSuccess(PaymentSuccessEventDTO event) {
        try {
            log.info("Publishing payment success event | ref={}", event.getReference());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PAYMENT_EXCHANGE,
                    RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY,
                    event
            );

            log.info("Payment success event published | ref={}", event.getReference());

        } catch (Exception ex) {
            log.error("Failed to publish payment success event | ref={} | error={}",
                    event.getReference(), ex.getMessage(), ex);
        }
    }

    /**
     * Publish payment failed event
     */
    public void publishPaymentFailed(PaymentFailedEventDTO event) {
        try {
            log.info("Publishing payment failed event | ref={}", event.getReference());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PAYMENT_EXCHANGE,
                    RabbitMQConfig.PAYMENT_FAILED_ROUTING_KEY,
                    event
            );

            log.info("Payment failed event published | ref={}", event.getReference());

        } catch (Exception ex) {
            log.error("Failed to publish payment failed event | ref={} | error={}",
                    event.getReference(), ex.getMessage(), ex);
        }
    }

    /**
     * Publish payment initialized event
     */
    public void publishPaymentInitialized(PaymentInitializedEventDTO event) {
        try {
            log.info("Publishing payment initialized event | ref={}", event.getReference());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PAYMENT_EXCHANGE,
                    RabbitMQConfig.PAYMENT_INITIALIZED_ROUTING_KEY,
                    event
            );

            log.info("Payment initialized event published | ref={}", event.getReference());

        } catch (Exception ex) {
            log.error("Failed to publish payment initialized event | ref={} | error={}",
                    event.getReference(), ex.getMessage(), ex);
        }
    }
}