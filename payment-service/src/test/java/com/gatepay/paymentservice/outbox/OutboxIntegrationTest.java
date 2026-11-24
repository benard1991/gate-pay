package com.gatepay.paymentservice.outbox;

import com.gatepay.paymentservice.config.RabbitMQConfig;
import com.gatepay.paymentservice.model.OutboxEvent;
import com.gatepay.paymentservice.model.enums.OutboxStatus;
import com.gatepay.paymentservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Transactional Outbox Integration Tests")
class OutboxIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("payment_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);

        // drive the relay manually in these tests instead of on a timer
        registry.add("outbox.publisher.poll-interval-ms", () -> "3600000");
    }

    @Autowired private OutboxService outboxService;
    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void clean() {
        outboxEventRepository.deleteAll();
        rabbitAdmin.purgeQueue(RabbitMQConfig.PAYMENT_SUCCESS_QUEUE, true);
    }

    private Map<String, Object> payload(String reference) {
        return Map.of(
                "userId", 1L,
                "amount", new BigDecimal("5000.00"),
                "reference", reference,
                "currency", "NGN",
                "event", "PAYMENT_SUCCESS");
    }

    private void saveInTransaction(String reference, String routingKey) {
        transactionTemplate.executeWithoutResult(status -> outboxService.save(
                "PaymentTransaction", reference, "PAYMENT_SUCCESS",
                RabbitMQConfig.PAYMENT_SUCCESS_EXCHANGE, routingKey, payload(reference)));
    }

    @Test
    @DisplayName("committed event is relayed to the payment.success queue and marked SENT")
    void relaysCommittedEvent() {
        saveInTransaction("REF-OUTBOX-1", RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY);

        outboxPublisher.publishPendingEvents();

        Object received = rabbitTemplate.receiveAndConvert(RabbitMQConfig.PAYMENT_SUCCESS_QUEUE, 5000);
        assertThat(received).isInstanceOf(String.class);
        assertThat((String) received)
                .contains("\"reference\":\"REF-OUTBOX-1\"")
                .contains("\"userId\":1")
                .contains("\"event\":\"PAYMENT_SUCCESS\"");

        OutboxEvent event = outboxEventRepository.findAll().get(0);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("event disappears when the business transaction rolls back")
    void rolledBackTransactionLeavesNoEvent() {
        transactionTemplate.executeWithoutResult(status -> {
            outboxService.save("PaymentTransaction", "REF-ROLLBACK", "PAYMENT_SUCCESS",
                    RabbitMQConfig.PAYMENT_SUCCESS_EXCHANGE,
                    RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY, payload("REF-ROLLBACK"));
            status.setRollbackOnly();
        });

        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("saving outside a transaction is rejected (MANDATORY propagation)")
    void requiresExistingTransaction() {
        assertThatThrownBy(() -> outboxService.save("PaymentTransaction", "REF-NO-TX", "PAYMENT_SUCCESS",
                RabbitMQConfig.PAYMENT_SUCCESS_EXCHANGE,
                RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY, payload("REF-NO-TX")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("unroutable message is NOT marked SENT and is scheduled for retry")
    void unroutableMessageStaysPending() {
        saveInTransaction("REF-UNROUTABLE", "no.queue.bound.here");

        outboxPublisher.publishPendingEvents();

        OutboxEvent event = outboxEventRepository.findAll().get(0);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("unroutable");
    }

    @Test
    @DisplayName("the same event cannot be recorded twice for one payment reference")
    void duplicateEventRejected() {
        saveInTransaction("REF-DUP", RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY);

        assertThatThrownBy(() -> saveInTransaction("REF-DUP", RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }
}
