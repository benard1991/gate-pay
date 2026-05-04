package com.gatepay.notificationservice.listener;

import com.gatepay.notificationservice.config.RabbitMQConfig;
import com.gatepay.notificationservice.dto.NotificationMessage;
import com.gatepay.notificationservice.service.NotificationHandlerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class NotificationListenerIntegrationTest {


    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.12-alpine")
            .withStartupTimeout(Duration.ofSeconds(60));

    @DynamicPropertySource
    static void registerRabbitMQProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private NotificationHandlerService handlerService;

    @BeforeEach
    void resetHandlerServiceMock() {
        reset(handlerService);
    }

    @Test
    void aValidMessagePublishedToTheExchangeIsPickedUpAndHandled() {
        NotificationMessage message = buildMessage("EMAIL", "user@gatepay.com");

        publishToExchange(message);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(handlerService).handle(any(NotificationMessage.class))
        );
    }

    @Test
    void theListenerCorrectlyDeserializesAllFieldsOfTheIncomingMessage() {
        NotificationMessage message = buildMessage("EMAIL", "fields@gatepay.com");

        publishToExchange(message);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(handlerService).handle(argThat(received ->
                        "EMAIL".equals(received.getType()) &&
                                "fields@gatepay.com".equals(received.getRecipient())
                ))
        );
    }

    @Test
    void aMessageThatFailsProcessingIsRoutedToTheRetryQueueNotLostImmediately() {
        doThrow(new RuntimeException("handler blew up"))
                .when(handlerService).handle(any());

        publishToExchange(buildMessage("EMAIL", "retry@gatepay.com"));

        // After failure, message should appear in the retry queue (not DLQ yet — TTL hasn't elapsed)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Object retryMessage = rabbitTemplate.receiveAndConvert(RabbitMQConfig.RETRY_QUEUE);
            assertThat(retryMessage).isNotNull();
        });
    }

    @Test
    void aMessageThatSitsInTheRetryQueueEventuallyMovesToTheDlqAfterTtlExpires() {
        doThrow(new RuntimeException("handler blew up"))
                .when(handlerService).handle(any());

        publishToExchange(buildMessage("EMAIL", "dlq@gatepay.com"));

        // TTL on retry queue is 10 seconds, so we wait beyond that
        // Message path: notification-queue → retry-queue (10s TTL) → DLQ
        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Object deadLetter = rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ_QUEUE);
                    assertThat(deadLetter).isNotNull();
                });
    }

    @Test
    void aSuccessfulMessageNeverReachesTheRetryQueueOrTheDlq() {
        // handlerService.handle() succeeds by default (no stubbing = no exception)
        publishToExchange(buildMessage("EMAIL", "success@gatepay.com"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(handlerService).handle(any())
        );

        // Give RabbitMQ a moment then confirm nothing landed in retry or DLQ
        assertThat(rabbitTemplate.receiveAndConvert(RabbitMQConfig.RETRY_QUEUE)).isNull();
        assertThat(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ_QUEUE)).isNull();
    }

    @Test
    void nothingIsProcessedWhenNoMessageIsPublished() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
        verifyNoInteractions(handlerService);
    }

    // --- helpers ---

    private void publishToExchange(NotificationMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                message
        );
    }

    private NotificationMessage buildMessage(String type, String recipient) {
        NotificationMessage message = new NotificationMessage();
        message.setType(type);
        message.setRecipient(recipient);
        return message;
    }
}