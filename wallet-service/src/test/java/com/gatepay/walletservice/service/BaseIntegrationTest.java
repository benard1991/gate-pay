package com.gatepay.walletservice.service;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Import(BaseIntegrationTest.ExternalQueuesConfig.class)
public abstract class BaseIntegrationTest {

    // -------------------------------------------------------------------------
    // Containers — static so they start once and are reused across all subclasses
    // -------------------------------------------------------------------------

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("walletdb_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.12-management")
            .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url",         mysql::getJdbcUrl);
        registry.add("spring.datasource.username",     mysql::getUsername);
        registry.add("spring.datasource.password",     mysql::getPassword);

        // RabbitMQ
        registry.add("spring.rabbitmq.host",     rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port",     rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    // -------------------------------------------------------------------------
    // External queue declarations
    //
    // Your wallet service consumes from queues owned by other services
    // (KYC service, Payment service, etc.). Those queues don't exist in
    // RabbitMQConfig because your service doesn't own them — but the
    // @RabbitListener registry does a passive check on startup and crashes
    // if they're missing. Declaring them here as plain durable queues is
    // enough to satisfy the broker without polluting production config.
    //
    // Add any new external queue here when you add a new @RabbitListener
    // that consumes from another service's queue.
    // -------------------------------------------------------------------------

    @TestConfiguration
    public static class ExternalQueuesConfig {

        @Bean
        public Queue kycApprovedQueue() {
            return new Queue("kyc.approved.queue", true);
        }

        @Bean
        public Queue paymentSuccessQueue() {
            return new Queue("payment.success.queue", true);
        }
    }
}