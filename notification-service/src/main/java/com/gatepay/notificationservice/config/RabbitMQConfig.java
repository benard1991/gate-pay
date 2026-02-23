package com.gatepay.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICATION_QUEUE = "notification-queue";
    public static final String NOTIFICATION_EXCHANGE = "notification-exchange";
    public static final String NOTIFICATION_ROUTING_KEY = "notification-routingKey";

    public static final String RETRY_QUEUE = "notification-queue.retry";
    public static final String DLQ_QUEUE = "notification-queue.dlq";

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RETRY_QUEUE)
                .build();
    }

    @Bean
    public Queue notificationRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_QUEUE)
                .withArgument("x-message-ttl", 10000) // 10 seconds before retry
                .build();
    }

    @Bean
    public Queue notificationDLQ() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Binding notificationBinding(@Qualifier("notificationQueue") Queue notificationQueue,
                                       DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(notificationExchange)
                .with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding notificationRetryBinding(@Qualifier("notificationRetryQueue") Queue notificationRetryQueue,
                                            DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationRetryQueue)
                .to(notificationExchange)
                .with(RETRY_QUEUE);
    }

    @Bean
    public Binding notificationDLQBinding(@Qualifier("notificationDLQ") Queue dlqQueue,
                                          DirectExchange notificationExchange) {
        return BindingBuilder.bind(dlqQueue)
                .to(notificationExchange)
                .with(DLQ_QUEUE);
    }

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

    // Listener factory for DLQ handling
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setDefaultRequeueRejected(false); // important for DLQ
        return factory;
    }
}
