package com.gatepay.kycservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ─────────────────────────────────────────
    // NOTIFICATION (publish only - no queue declaration)
    // ─────────────────────────────────────────
    public static final String NOTIFICATION_EXCHANGE    = "notification-exchange";
    public static final String NOTIFICATION_ROUTING_KEY = "notification-routingKey";

    // ─────────────────────────────────────────
    // KYC APPROVED
    // ─────────────────────────────────────────
    public static final String KYC_APPROVED_EXCHANGE    = "kyc.exchange";
    public static final String KYC_APPROVED_ROUTING_KEY = "kyc.approved";
    public static final String KYC_APPROVED_QUEUE       = "kyc.approved.queue";

    // ─────────────────────────────────────────
    // DLQ
    // ─────────────────────────────────────────
    public static final String KYC_DLQ_EXCHANGE         = "kyc.dlq.exchange";
    public static final String KYC_APPROVED_DLQ         = "kyc.approved.dlq";

    // ─────────────────────────────────────────
    // DLQ EXCHANGE
    // ─────────────────────────────────────────
    @Bean
    public DirectExchange kycDlqExchange() {
        return new DirectExchange(KYC_DLQ_EXCHANGE, true, false);
    }

    // ─────────────────────────────────────────
    // KYC APPROVED BEANS + DLQ
    // ─────────────────────────────────────────
    @Bean
    public Queue kycApprovedQueue() {
        return QueueBuilder.durable(KYC_APPROVED_QUEUE)
                .withArgument("x-dead-letter-exchange", KYC_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", KYC_APPROVED_DLQ)
                .build();
    }

    @Bean
    public Queue kycApprovedDlq() {
        return new Queue(KYC_APPROVED_DLQ, true);
    }

    @Bean
    public Binding kycApprovedDlqBinding() {
        return BindingBuilder.bind(kycApprovedDlq())
                .to(kycDlqExchange())
                .with(KYC_APPROVED_DLQ);
    }

    @Bean
    public TopicExchange kycApprovedExchange() {
        return new TopicExchange(KYC_APPROVED_EXCHANGE, true, false);
    }

    @Bean
    public Binding kycApprovedBinding() {
        return BindingBuilder
                .bind(kycApprovedQueue())
                .to(kycApprovedExchange())
                .with(KYC_APPROVED_ROUTING_KEY);
    }

    // ─────────────────────────────────────────
    // SHARED BEANS
    // ─────────────────────────────────────────
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}