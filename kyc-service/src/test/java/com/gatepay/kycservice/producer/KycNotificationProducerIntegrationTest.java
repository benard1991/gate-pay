package com.gatepay.kycservice.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.kycservice.config.RabbitMQConfig;
import com.gatepay.kycservice.dto.NotificationMessage;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("KycNotificationProducer Integration Tests")
class KycNotificationProducerIntegrationTest {

    private static final String QUEUE_NAME = "kyc-test-queue";

    @Container
    static RabbitMQContainer rabbitMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("test_db")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @TestConfiguration
    static class TestRabbitConfig {

        @Bean
        public DirectExchange notificationExchange() {
            return new DirectExchange(RabbitMQConfig.NOTIFICATION_EXCHANGE, true, false);
        }

        @Bean
        public Queue kycTestQueue() {
            return new Queue(QUEUE_NAME, true);
        }

        @Bean
        public Binding kycTestBinding(
                @Qualifier("kycTestQueue") Queue kycTestQueue,
                @Qualifier("notificationExchange") DirectExchange notificationExchange) {
            return BindingBuilder
                    .bind(kycTestQueue)
                    .to(notificationExchange)
                    .with(RabbitMQConfig.NOTIFICATION_ROUTING_KEY);
        }

        @Bean
        public Binding kycApprovedTestBinding(
                @Qualifier("kycTestQueue") Queue kycTestQueue,
                @Qualifier("kycApprovedExchange") TopicExchange kycApprovedExchange) {
            return BindingBuilder
                    .bind(kycTestQueue)
                    .to(kycApprovedExchange)
                    .with(RabbitMQConfig.KYC_APPROVED_ROUTING_KEY);
        }
    }

    @Autowired
    private KycNotificationProducer producer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearQueue() {
        rabbitTemplate.receive(QUEUE_NAME);
    }

    @Test
    @DisplayName("should publish KYC notification message")
    void should_publish_kyc_notification_message() {

        NotificationMessage message = new NotificationMessage(
                "user@example.com",
                "EMAIL",
                "KYC Update",
                "Your KYC is processing",
                Map.of("kycId", 100L)
        );

        producer.sendKycNotification(message);

        Object received = rabbitTemplate.receiveAndConvert(QUEUE_NAME, 5000);

        assertThat(received).isNotNull();
    }

    @Test
    @DisplayName("publishConfirmed delivers the outbox payload to the approved queue")
    void publish_confirmed_reaches_approved_queue() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "userId", 123, "kycId", 7, "event", "KYC_APPROVED"));

        // throws if the broker doesn't ack or nothing is bound to the key
        producer.publishConfirmed("kyc.exchange", "kyc.approved", payload);

        String json = (String) rabbitTemplate.receiveAndConvert(QUEUE_NAME, 5000);
        assertThat(json).isNotNull();

        Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
        assertThat(parsed.get("userId")).isEqualTo(123);
        assertThat(parsed.get("event")).isEqualTo("KYC_APPROVED");
    }

    @Test
    @DisplayName("publishConfirmed fails when no queue is bound to the routing key")
    void publish_confirmed_fails_when_unroutable() {
        assertThatThrownBy(() -> producer.publishConfirmed("kyc.exchange", "kyc.nobody-listens", "{}"))
                .hasMessageContaining("unroutable");
    }
}