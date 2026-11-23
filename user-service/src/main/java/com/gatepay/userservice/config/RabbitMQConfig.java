package com.gatepay.userservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification-exchange";
    public static final String NOTIFICATION_ROUTING_KEY = "notification-routingKey";

    // KYC decisions published by kyc-service (through its outbox)
    public static final String KYC_EXCHANGE         = "kyc.exchange";
    public static final String KYC_APPROVED_KEY     = "kyc.approved";
    public static final String KYC_REJECTED_KEY     = "kyc.rejected";
    public static final String KYC_STATUS_QUEUE     = "user.kyc.status.queue";
    public static final String KYC_STATUS_DLQ       = "user.kyc.status.dlq";

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

    @Bean
    public TopicExchange kycExchange() {
        return new TopicExchange(KYC_EXCHANGE, true, false);
    }

    // messages that keep failing end up here instead of being dropped
    @Bean
    public Queue kycStatusQueue() {
        return QueueBuilder.durable(KYC_STATUS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", KYC_STATUS_DLQ)
                .build();
    }

    @Bean
    public Queue kycStatusDlq() {
        return QueueBuilder.durable(KYC_STATUS_DLQ).build();
    }

    @Bean
    public Binding kycApprovedToUserBinding() {
        return BindingBuilder.bind(kycStatusQueue()).to(kycExchange()).with(KYC_APPROVED_KEY);
    }

    @Bean
    public Binding kycRejectedToUserBinding() {
        return BindingBuilder.bind(kycStatusQueue()).to(kycExchange()).with(KYC_REJECTED_KEY);
    }
}
