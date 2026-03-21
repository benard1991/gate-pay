package com.gatepay.paymentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_SUCCESS_EXCHANGE    = "payment.exchange";
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "payment.success";
    public static final String PAYMENT_SUCCESS_QUEUE       = "payment.success.queue";
    public static final String PAYMENT_DLQ_EXCHANGE        = "payment.dlq.exchange";
    public static final String PAYMENT_SUCCESS_DLQ         = "payment.success.dlq";

    // ─────────────────────────────────────────
    // DLQ EXCHANGE
    // ─────────────────────────────────────────
    @Bean
    public DirectExchange paymentDlqExchange() {
        return new DirectExchange(PAYMENT_DLQ_EXCHANGE, true, false);
    }

    // ─────────────────────────────────────────
    // PAYMENT EXCHANGE
    // ─────────────────────────────────────────
    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_SUCCESS_EXCHANGE, true, false);
    }

    // ─────────────────────────────────────────
    // PAYMENT SUCCESS QUEUE + DLQ
    // ─────────────────────────────────────────
    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PAYMENT_SUCCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_SUCCESS_DLQ)
                .build();
    }

    @Bean
    public Queue paymentSuccessDlq() {
        return new Queue(PAYMENT_SUCCESS_DLQ, true);
    }

    @Bean
    public Binding paymentSuccessDlqBinding() {
        return BindingBuilder
                .bind(paymentSuccessDlq())
                .to(paymentDlqExchange())
                .with(PAYMENT_SUCCESS_DLQ);
    }

    // ─────────────────────────────────────────
    // PAYMENT SUCCESS BINDING
    // ─────────────────────────────────────────
    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder
                .bind(paymentSuccessQueue())
                .to(paymentExchange())
                .with(PAYMENT_SUCCESS_ROUTING_KEY);
    }

    // ─────────────────────────────────────────
    // JSON MESSAGE CONVERTER
    // ─────────────────────────────────────────
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}