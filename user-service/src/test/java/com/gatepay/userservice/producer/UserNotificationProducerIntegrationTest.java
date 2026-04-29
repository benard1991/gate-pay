package com.gatepay.userservice.producer;

import com.gatepay.userservice.config.RabbitMQConfig;
import com.gatepay.userservice.dto.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("UserNotificationProducer Integration Tests")
class UserNotificationProducerIntegrationTest {

    // Real RabbitMQ — this is what we're actually testing against
    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine")
    );

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // MySQL — needed to satisfy the full Spring context
        registry.add("spring.datasource.url",                mysql::getJdbcUrl);
        registry.add("spring.datasource.username",           mysql::getUsername);
        registry.add("spring.datasource.password",           mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // RabbitMQ — what we're actually testing
        registry.add("spring.rabbitmq.host",     rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port",     rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    // We need a queue bound to the exchange so messages don't just vanish —
    // without this we can't read back what was published
    @TestConfiguration
    static class TestRabbitSetup {

        @Bean
        public Queue testNotificationQueue() {
            return new Queue("test-notification-queue", false);
        }

        @Bean
        public DirectExchange notificationExchange() {
            return new DirectExchange(RabbitMQConfig.NOTIFICATION_EXCHANGE);
        }

        @Bean
        public Binding notificationBinding(Queue testNotificationQueue,
                                           DirectExchange notificationExchange) {
            return BindingBuilder
                    .bind(testNotificationQueue)
                    .to(notificationExchange)
                    .with(RabbitMQConfig.NOTIFICATION_ROUTING_KEY);
        }

        @Bean
        public RabbitAdmin rabbitAdmin(
                org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }
    }

    @Autowired
    private UserNotificationProducer producer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String TEST_EMAIL = "ben@example.com";
    private static final String TEST_OTP   = "123456";
    private static final String TEST_QUEUE = "test-notification-queue";

    @BeforeEach
    void clearQueue() {
        // drain any leftover messages from a previous test so they don't interfere
        while (rabbitTemplate.receive(TEST_QUEUE) != null) {
            // just draining
        }
    }

    @Nested
    @DisplayName("sendOtpEmail()")
    class SendOtpEmailTests {

        @Test
        @DisplayName("Publishes a message to the correct exchange with the correct routing key")
        void publishes_message_to_correct_exchange() {
            producer.sendOtpEmail(TEST_EMAIL, TEST_OTP);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
        }

        @Test
        @DisplayName("Message is sent to the correct recipient")
        void message_has_correct_recipient() {
            producer.sendOtpEmail(TEST_EMAIL, TEST_OTP);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getRecipient()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("Message type is EMAIL")
        void message_type_is_email() {
            producer.sendOtpEmail(TEST_EMAIL, TEST_OTP);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getType()).isEqualTo("EMAIL");
        }

        @Test
        @DisplayName("Subject says Password Reset OTP")
        void message_has_correct_subject() {
            producer.sendOtpEmail(TEST_EMAIL, TEST_OTP);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getSubject()).isEqualTo("Password Reset OTP");
        }

        @Test
        @DisplayName("Body contains the OTP code")
        void message_body_contains_otp() {
            producer.sendOtpEmail(TEST_EMAIL, TEST_OTP);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getBody()).contains(TEST_OTP);
        }

        @Test
        @DisplayName("Metadata is empty")
        void message_metadata_is_empty() {
            producer.sendOtpEmail(TEST_EMAIL, TEST_OTP);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getMetadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("sendResetPasswordEmail()")
    class SendResetPasswordEmailTests {

        @Test
        @DisplayName("Publishes a message to the correct exchange with the correct routing key")
        void publishes_message_to_correct_exchange() {
            producer.sendResetPasswordEmail(TEST_EMAIL);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
        }

        @Test
        @DisplayName("Message is sent to the correct recipient")
        void message_has_correct_recipient() {
            producer.sendResetPasswordEmail(TEST_EMAIL);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getRecipient()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("Message type is EMAIL")
        void message_type_is_email() {
            producer.sendResetPasswordEmail(TEST_EMAIL);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getType()).isEqualTo("EMAIL");
        }

        @Test
        @DisplayName("Subject says Reset Your Password")
        void message_has_correct_subject() {
            producer.sendResetPasswordEmail(TEST_EMAIL);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getSubject()).isEqualTo("Reset Your Password");
        }

        @Test
        @DisplayName("Body mentions password reset instructions")
        void message_body_mentions_password_reset() {
            producer.sendResetPasswordEmail(TEST_EMAIL);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getBody()).containsIgnoringCase("password");
        }

        @Test
        @DisplayName("Metadata is empty")
        void message_metadata_is_empty() {
            producer.sendResetPasswordEmail(TEST_EMAIL);

            NotificationMessage received = (NotificationMessage) rabbitTemplate
                    .receiveAndConvert(TEST_QUEUE, 3000);

            assertThat(received).isNotNull();
            assertThat(received.getMetadata()).isEmpty();
        }
    }
}