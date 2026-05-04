package com.gatepay.paymentservice.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.config.RabbitMQConfig;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("PaymentEventProducer Integration Tests")
class PaymentEventProducerIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")
    )
            .withDatabaseName("payment_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management")
    );

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // RabbitMQ
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private PaymentEventProducer producer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void purgeQueue() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.PAYMENT_SUCCESS_QUEUE, true);
    }


    // sendPaymentSuccessEvent
    
    @Nested
    @DisplayName("sendPaymentSuccessEvent()")
    class SendPaymentSuccessEvent {

        @Test
        @DisplayName("Should publish message to the correct queue")
        void shouldPublishMessageToCorrectQueue() throws Exception {
            Map<String, Object> payload = Map.of(
                    "reference", "PAY-REF-001",
                    "userId", 1L,
                    "amount", "5000.00",
                    "currency", "NGN",
                    "status", "SUCCESS"
            );

            producer.sendPaymentSuccessEvent(payload);

            Message received = rabbitTemplate.receive(
                    RabbitMQConfig.PAYMENT_SUCCESS_QUEUE, 3000
            );

            assertThat(received).isNotNull();
        }

        @Test
        @DisplayName("Should publish message with the correct reference")
        void shouldPublishMessageWithCorrectReference() throws Exception {
            Map<String, Object> payload = Map.of(
                    "reference", "PAY-REF-002",
                    "userId", 2L,
                    "amount", "10000.00",
                    "currency", "NGN",
                    "status", "SUCCESS"
            );

            producer.sendPaymentSuccessEvent(payload);

            Message received = rabbitTemplate.receive(
                    RabbitMQConfig.PAYMENT_SUCCESS_QUEUE, 3000
            );

            assertThat(received).isNotNull();
            String body = new String(received.getBody());
            String json = objectMapper.readValue(body, String.class);
            Map<String, Object> receivedPayload = objectMapper.readValue(json, Map.class);
            assertThat(receivedPayload.get("reference")).isEqualTo("PAY-REF-002");
        }

        @Test
        @DisplayName("Should publish message containing all expected fields")
        void shouldPublishMessageWithAllFields() throws Exception {
            Map<String, Object> payload = Map.of(
                    "reference", "PAY-REF-003",
                    "userId", 3L,
                    "amount", "7500.00",
                    "currency", "NGN",
                    "status", "SUCCESS"
            );

            producer.sendPaymentSuccessEvent(payload);

            Message received = rabbitTemplate.receive(
                    RabbitMQConfig.PAYMENT_SUCCESS_QUEUE, 3000
            );

            assertThat(received).isNotNull();
            String body = new String(received.getBody());
            String json = objectMapper.readValue(body, String.class);
            Map<String, Object> receivedPayload = objectMapper.readValue(json, Map.class);

            assertThat(receivedPayload).containsKey("reference");
            assertThat(receivedPayload).containsKey("userId");
            assertThat(receivedPayload).containsKey("amount");
            assertThat(receivedPayload).containsKey("currency");
            assertThat(receivedPayload).containsKey("status");
            assertThat(receivedPayload.get("amount")).isEqualTo("7500.00");
            assertThat(receivedPayload.get("currency")).isEqualTo("NGN");
            assertThat(receivedPayload.get("status")).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("Should not throw an exception when payload is empty")
        void shouldNotThrowWhenPayloadIsEmpty() {
            Map<String, Object> emptyPayload = Map.of();

            Assertions.assertDoesNotThrow(
                    () -> producer.sendPaymentSuccessEvent(emptyPayload)
            );
        }
    }
}