package com.gatepay.walletservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${wallet.rabbitmq.exchange:wallet.exchange}")
    private String exchange;

    @Value("${wallet.rabbitmq.routing-key.credit:wallet.credit}")
    private String creditRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.debit:wallet.debit}")
    private String debitRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.reversal:wallet.reversal}")
    private String reversalRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.created:wallet.created}")
    private String createdRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.suspended:wallet.suspended}")
    private String suspendedRoutingKey;

    @Value("${kyc.rabbitmq.queue.approved:kyc.approved.queue}")
    private String kycApprovedQueue;

    @Value("${payment.rabbitmq.queue.success:payment.success.queue}")
    private String paymentSuccessQueue;

    // ─────────────────────────────────────────
    // DLQ EXCHANGE
    // ─────────────────────────────────────────
    private static final String DLQ_EXCHANGE = "wallet.dlq.exchange";

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE, true, false);
    }

    // ─────────────────────────────────────────
    // WALLET EXCHANGE
    // ─────────────────────────────────────────
    @Bean
    public TopicExchange walletExchange() {
        return new TopicExchange(exchange, true, false);
    }

    // ─────────────────────────────────────────
    // WALLET QUEUES + DLQ
    // ─────────────────────────────────────────
    @Bean
    public Queue walletCreditQueue() {
        return QueueBuilder.durable("wallet.credit.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "wallet.credit.dlq")
                .build();
    }

    @Bean
    public Queue walletCreditDlq() {
        return new Queue("wallet.credit.dlq", true);
    }

    @Bean
    public Binding walletCreditDlqBinding() {
        return BindingBuilder.bind(walletCreditDlq()).to(dlqExchange()).with("wallet.credit.dlq");
    }

    @Bean
    public Queue walletDebitQueue() {
        return QueueBuilder.durable("wallet.debit.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "wallet.debit.dlq")
                .build();
    }

    @Bean
    public Queue walletDebitDlq() {
        return new Queue("wallet.debit.dlq", true);
    }

    @Bean
    public Binding walletDebitDlqBinding() {
        return BindingBuilder.bind(walletDebitDlq()).to(dlqExchange()).with("wallet.debit.dlq");
    }

    @Bean
    public Queue walletReversalQueue() {
        return QueueBuilder.durable("wallet.reversal.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "wallet.reversal.dlq")
                .build();
    }

    @Bean
    public Queue walletReversalDlq() {
        return new Queue("wallet.reversal.dlq", true);
    }

    @Bean
    public Binding walletReversalDlqBinding() {
        return BindingBuilder.bind(walletReversalDlq()).to(dlqExchange()).with("wallet.reversal.dlq");
    }

    @Bean
    public Queue walletCreatedQueue() {
        return QueueBuilder.durable("wallet.created.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "wallet.created.dlq")
                .build();
    }

    @Bean
    public Queue walletCreatedDlq() {
        return new Queue("wallet.created.dlq", true);
    }

    @Bean
    public Binding walletCreatedDlqBinding() {
        return BindingBuilder.bind(walletCreatedDlq()).to(dlqExchange()).with("wallet.created.dlq");
    }

    @Bean
    public Queue walletSuspendedQueue() {
        return QueueBuilder.durable("wallet.suspended.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "wallet.suspended.dlq")
                .build();
    }

    @Bean
    public Queue walletSuspendedDlq() {
        return new Queue("wallet.suspended.dlq", true);
    }

    @Bean
    public Binding walletSuspendedDlqBinding() {
        return BindingBuilder.bind(walletSuspendedDlq()).to(dlqExchange()).with("wallet.suspended.dlq");
    }

    // ─────────────────────────────────────────
    // KYC APPROVED QUEUE + DLQ (consumer only)
    // ─────────────────────────────────────────
    @Bean
    public Queue kycApprovedQueue() {
        return QueueBuilder.durable(kycApprovedQueue)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "kyc.approved.dlq")
                .build();
    }

    @Bean
    public Queue kycApprovedDlq() {
        return new Queue("kyc.approved.dlq", true);
    }

    @Bean
    public Binding kycApprovedDlqBinding() {
        return BindingBuilder.bind(kycApprovedDlq()).to(dlqExchange()).with("kyc.approved.dlq");
    }

    // ─────────────────────────────────────────
    // PAYMENT SUCCESS QUEUE + DLQ (consumer only)
    // ─────────────────────────────────────────
    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(paymentSuccessQueue)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "payment.success.dlq")
                .build();
    }

    @Bean
    public Queue paymentSuccessDlq() {
        return new Queue("payment.success.dlq", true);
    }

    @Bean
    public Binding paymentSuccessDlqBinding() {
        return BindingBuilder.bind(paymentSuccessDlq()).to(dlqExchange()).with("payment.success.dlq");
    }

    // ─────────────────────────────────────────
    // WALLET BINDINGS
    // ─────────────────────────────────────────
    @Bean
    public Binding creditBinding() {
        return BindingBuilder.bind(walletCreditQueue()).to(walletExchange()).with(creditRoutingKey);
    }

    @Bean
    public Binding debitBinding() {
        return BindingBuilder.bind(walletDebitQueue()).to(walletExchange()).with(debitRoutingKey);
    }

    @Bean
    public Binding reversalBinding() {
        return BindingBuilder.bind(walletReversalQueue()).to(walletExchange()).with(reversalRoutingKey);
    }

    @Bean
    public Binding createdBinding() {
        return BindingBuilder.bind(walletCreatedQueue()).to(walletExchange()).with(createdRoutingKey);
    }

    @Bean
    public Binding suspendedBinding() {
        return BindingBuilder.bind(walletSuspendedQueue()).to(walletExchange()).with(suspendedRoutingKey);
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