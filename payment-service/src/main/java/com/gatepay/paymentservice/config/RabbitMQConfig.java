package com.gatepay.paymentservice.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange names
    public static final String PAYMENT_EXCHANGE = "payment.exchange";

    // Queue names
    public static final String PAYMENT_SUCCESS_QUEUE = "payment.success.queue";
    public static final String PAYMENT_FAILED_QUEUE = "payment.failed.queue";
    public static final String PAYMENT_INITIALIZED_QUEUE = "payment.initialized.queue";

    // Routing keys
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "payment.success";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String PAYMENT_INITIALIZED_ROUTING_KEY = "payment.initialized";

    // Dead Letter Queue (for failed message processing)
    public static final String PAYMENT_DLQ = "payment.dlq";
    public static final String PAYMENT_DLX = "payment.dlx";

    /**
     * Main exchange for all payment events
     */
    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    /**
     * Dead Letter Exchange for failed messages
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(PAYMENT_DLX);
    }

    /**
     * Payment Success Queue
     */
    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PAYMENT_SUCCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX)
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    /**
     * Payment Failed Queue
     */
    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(PAYMENT_FAILED_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX)
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    /**
     * Payment Initialized Queue
     */
    @Bean
    public Queue paymentInitializedQueue() {
        return QueueBuilder.durable(PAYMENT_INITIALIZED_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX)
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    /**
     * Dead Letter Queue
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    /**
     * Bind success queue to exchange
     */
    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder
                .bind(paymentSuccessQueue())
                .to(paymentExchange())
                .with(PAYMENT_SUCCESS_ROUTING_KEY);
    }

    /**
     * Bind failed queue to exchange
     */
    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder
                .bind(paymentFailedQueue())
                .to(paymentExchange())
                .with(PAYMENT_FAILED_ROUTING_KEY);
    }

    /**
     * Bind initialized queue to exchange
     */
    @Bean
    public Binding paymentInitializedBinding() {
        return BindingBuilder
                .bind(paymentInitializedQueue())
                .to(paymentExchange())
                .with(PAYMENT_INITIALIZED_ROUTING_KEY);
    }

    /**
     * Bind DLQ to DLX
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dlq");
    }

    /**
     * JSON message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}