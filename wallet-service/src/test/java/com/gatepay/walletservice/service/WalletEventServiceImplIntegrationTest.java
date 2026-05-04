package com.gatepay.walletservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.walletservice.client.UserServiceClient;
import com.gatepay.walletservice.dto.ApiResponse;
import com.gatepay.walletservice.dto.UserDto;
import com.gatepay.walletservice.dto.external.NotificationMessage;
import com.gatepay.walletservice.enums.TransactionSource;
import com.gatepay.walletservice.enums.TransactionStatus;
import com.gatepay.walletservice.enums.TransactionType;
import com.gatepay.walletservice.model.Wallet;
import com.gatepay.walletservice.model.WalletTransaction;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletEventServiceImplIntegrationTest extends BaseIntegrationTest {


    @Autowired private WalletEventService    walletEventService;
    @Autowired private RabbitTemplate        rabbitTemplate;
    @Autowired private RabbitAdmin           rabbitAdmin;
    @Autowired private ObjectMapper          objectMapper;

    // UserServiceClient talks to an external service — keep it mocked
    @MockBean  private UserServiceClient     userServiceClient;

   
    @Value("${wallet.rabbitmq.exchange:wallet.exchange}")
    private String walletExchange;

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

    @Value("${notification.rabbitmq.exchange:notification.exchange}")
    private String notificationExchange;

    @Value("${notification.rabbitmq.routing-key:notification.routing.key}")
    private String notificationRoutingKey;

    // Test queue names — declared per-test so assertions never bleed across tests

    private static final String WALLET_TEST_QUEUE       = "test.wallet.events";
    private static final String NOTIFICATION_TEST_QUEUE = "test.notification.events";


    @BeforeEach
    void bindTestQueues() {
        // rabbitAdmin.declareQueue() only tracks beans in its internal registry —
        // queues declared programmatically are not sent to the broker until the
        // admin initialises its own connection, which is lazy. Using the channel
        // directly bypasses this and guarantees the queues exist before any test runs.
        rabbitTemplate.execute(channel -> {
            // Wallet events queue — non-durable, exclusive=false, auto-delete.
            // wallet.exchange is declared by RabbitMQConfig on startup so it already exists.
            channel.queueDeclare(WALLET_TEST_QUEUE, false, false, false, null);

            for (String key : new String[]{
                    creditRoutingKey, debitRoutingKey, reversalRoutingKey,
                    createdRoutingKey, suspendedRoutingKey }) {
                channel.queueBind(WALLET_TEST_QUEUE, walletExchange, key);
            }

            // notification.exchange belongs to the notification service — it is never
            // declared by this app's RabbitMQConfig, so we must declare it here before
            // we can bind the test queue to it. topic, durable, not auto-delete.
            channel.exchangeDeclare(notificationExchange, "topic", true, false, null);
            channel.queueDeclare(NOTIFICATION_TEST_QUEUE, false, false, false, null);
            channel.queueBind(NOTIFICATION_TEST_QUEUE, notificationExchange, notificationRoutingKey);

            return null;
        });
    }

    @AfterEach
    void cleanupTestQueues() {
        // Delete rather than purge — purge on a non-existent queue throws NOT_FOUND
        // which closes the channel and causes connection resets in the listener containers.
        // Delete with ifUnused=false, ifEmpty=false is safe even if the queue is missing
        // because we catch the exception explicitly.
        rabbitTemplate.execute(channel -> {
            try { channel.queueDelete(WALLET_TEST_QUEUE,       false, false); } catch (Exception ignored) {}
            try { channel.queueDelete(NOTIFICATION_TEST_QUEUE, false, false); } catch (Exception ignored) {}
            return null;
        });
    }


    private Wallet wallet() {
        Wallet w = new Wallet();
        w.setId(1L);
        w.setUserId(42L);
        w.setBalance(new BigDecimal("1200.00"));
        w.setLedgerBalance(new BigDecimal("1200.00"));
        return w;
    }

    private WalletTransaction creditTx() {
        return WalletTransaction.builder()
                .id(10L)
                .wallet(wallet())
                .reference("WLT-CREDIT-001")
                .type(TransactionType.CREDIT)
                .source(TransactionSource.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("200.00"))
                .balanceBefore(new BigDecimal("1000.00"))
                .balanceAfter(new BigDecimal("1200.00"))
                .description("Top-up")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private WalletTransaction debitTx() {
        return WalletTransaction.builder()
                .id(11L)
                .wallet(wallet())
                .reference("WLT-DEBIT-001")
                .type(TransactionType.DEBIT)
                .source(TransactionSource.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("300.00"))
                .balanceBefore(new BigDecimal("1000.00"))
                .balanceAfter(new BigDecimal("700.00"))
                .description("Payment")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private WalletTransaction reversalTx() {
        return WalletTransaction.builder()
                .id(12L)
                .wallet(wallet())
                .reference("WLT-REVERSAL-001")
                .type(TransactionType.CREDIT)
                .source(TransactionSource.REVERSAL)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("300.00"))
                .balanceBefore(new BigDecimal("700.00"))
                .balanceAfter(new BigDecimal("1000.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void stubUserEmail(String email) {
        UserDto userDto = new UserDto();
        userDto.setEmail(email);
        ApiResponse<UserDto> apiResponse = new ApiResponse<>();
        apiResponse.setData(userDto);
        when(userServiceClient.getUserProfile(42L)).thenReturn(apiResponse);
    }


    private Map<String, Object> receiveWalletEvent() throws Exception {
        String raw = receiveRawMessage(WALLET_TEST_QUEUE);
        // the payload is a JSON string serialized inside another JSON string
        String inner = objectMapper.readValue(raw, String.class);
        return objectMapper.readValue(inner, new TypeReference<>() {});
    }

    private NotificationMessage receiveNotificationEvent() throws Exception {
        String raw = receiveRawMessage(NOTIFICATION_TEST_QUEUE);
        return objectMapper.readValue(raw, NotificationMessage.class);
    }

    private String receiveRawMessage(String queue) {
        // Awaitility keeps polling until the broker delivers or the timeout hits.
        // This is more reliable than a bare receive() with a hard sleep.
        final String[] result = {null};
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Message msg = rabbitTemplate.receive(queue, 500);
            if (msg != null) {
                result[0] = new String(msg.getBody());
                return true;
            }
            return false;
        });
        return result[0];
    }

    // publishCreditEvent()

    @Test
    @Order(1)
    @DisplayName("publishCreditEvent sends wallet payload to the credit routing key")
    void creditEventLandsOnWalletQueue() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishCreditEvent(creditTx());

        Map<String, Object> payload = receiveWalletEvent();

        assertThat(payload.get("reference")).isEqualTo("WLT-CREDIT-001");
        assertThat(payload.get("type")).isEqualTo("CREDIT");
        assertThat(payload.get("userId").toString()).isEqualTo("42");
        assertThat(payload.get("walletId").toString()).isEqualTo("1");
        assertThat(payload.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    @Order(2)
    @DisplayName("publishCreditEvent sends a notification with the correct subject and recipient")
    void creditEventSendsNotification() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishCreditEvent(creditTx());

        // consume the wallet event first so the queue is clean for the notification poll
        receiveRawMessage(WALLET_TEST_QUEUE);

        NotificationMessage notification = receiveNotificationEvent();

        assertThat(notification.getRecipient()).isEqualTo("user@gatepay.com");
        assertThat(notification.getSubject()).isEqualTo("Wallet Credited");
        assertThat(notification.getBody()).contains("WLT-CREDIT-001");
        assertThat(notification.getBody()).contains("200");
    }

    // publishDebitEvent()

    @Test
    @Order(3)
    @DisplayName("publishDebitEvent sends wallet payload to the debit routing key")
    void debitEventLandsOnWalletQueue() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishDebitEvent(debitTx());

        Map<String, Object> payload = receiveWalletEvent();

        assertThat(payload.get("reference")).isEqualTo("WLT-DEBIT-001");
        assertThat(payload.get("type")).isEqualTo("DEBIT");
        assertThat(payload.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    @Order(4)
    @DisplayName("publishDebitEvent sends a notification with debit subject")
    void debitEventSendsNotification() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishDebitEvent(debitTx());

        receiveRawMessage(WALLET_TEST_QUEUE);

        NotificationMessage notification = receiveNotificationEvent();

        assertThat(notification.getRecipient()).isEqualTo("user@gatepay.com");
        assertThat(notification.getSubject()).isEqualTo("Wallet Debited");
        assertThat(notification.getBody()).contains("WLT-DEBIT-001");
    }


    // publishReversalEvent()

    @Test
    @Order(5)
    @DisplayName("publishReversalEvent sends wallet payload to the reversal routing key")
    void reversalEventLandsOnWalletQueue() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishReversalEvent(reversalTx());

        Map<String, Object> payload = receiveWalletEvent();

        assertThat(payload.get("reference")).isEqualTo("WLT-REVERSAL-001");
        assertThat(payload.get("type")).isEqualTo("CREDIT");  // reversal of a debit is a credit
        assertThat(payload.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    @Order(6)
    @DisplayName("publishReversalEvent sends a notification with reversal subject")
    void reversalEventSendsNotification() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishReversalEvent(reversalTx());

        receiveRawMessage(WALLET_TEST_QUEUE);

        NotificationMessage notification = receiveNotificationEvent();

        assertThat(notification.getSubject()).isEqualTo("Transaction Reversed");
        assertThat(notification.getBody()).contains("WLT-REVERSAL-001");
    }

    // publishWalletCreatedEvent()


    @Test
    @Order(7)
    @DisplayName("publishWalletCreatedEvent sends a wallet created payload with userId and currency")
    void walletCreatedEventLandsOnWalletQueue() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishWalletCreatedEvent(42L, "NGN");

        Map<String, Object> payload = receiveWalletEvent();

        assertThat(payload.get("event")).isEqualTo("WALLET_CREATED");
        assertThat(payload.get("userId").toString()).isEqualTo("42");
        assertThat(payload.get("currency")).isEqualTo("NGN");
    }

    @Test
    @Order(8)
    @DisplayName("publishWalletCreatedEvent sends a notification with wallet created subject")
    void walletCreatedEventSendsNotification() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishWalletCreatedEvent(42L, "NGN");

        receiveRawMessage(WALLET_TEST_QUEUE);

        NotificationMessage notification = receiveNotificationEvent();

        assertThat(notification.getRecipient()).isEqualTo("user@gatepay.com");
        assertThat(notification.getSubject()).isEqualTo("Wallet Created Successfully");
        assertThat(notification.getBody()).contains("NGN");
    }


    // publishWalletSuspendedEvent()

    @Test
    @Order(9)
    @DisplayName("publishWalletSuspendedEvent sends a wallet suspended payload with userId")
    void walletSuspendedEventLandsOnWalletQueue() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishWalletSuspendedEvent(42L);

        Map<String, Object> payload = receiveWalletEvent();

        assertThat(payload.get("event")).isEqualTo("WALLET_SUSPENDED");
        assertThat(payload.get("userId").toString()).isEqualTo("42");
    }

    @Test
    @Order(10)
    @DisplayName("publishWalletSuspendedEvent sends a notification asking the user to contact support")
    void walletSuspendedEventSendsNotification() throws Exception {
        stubUserEmail("user@gatepay.com");

        walletEventService.publishWalletSuspendedEvent(42L);

        receiveRawMessage(WALLET_TEST_QUEUE);

        NotificationMessage notification = receiveNotificationEvent();

        assertThat(notification.getSubject()).isEqualTo("Wallet Suspended");
        assertThat(notification.getBody()).containsIgnoringCase("support");
    }

    // Failure resilience — the silent catch blocks


    @Test
    @Order(11)
    @DisplayName("publishCreditEvent still delivers the wallet event when user service is down")
    void walletEventStillPublishedWhenUserServiceFails() throws Exception {
        // UserServiceClient throws — simulates the user service being unavailable
        when(userServiceClient.getUserProfile(42L))
                .thenThrow(new RuntimeException("user service is down"));

        // should not throw — the catch block in publishNotification swallows it
        walletEventService.publishCreditEvent(creditTx());

        // the wallet event must still land
        Map<String, Object> payload = receiveWalletEvent();
        assertThat(payload.get("reference")).isEqualTo("WLT-CREDIT-001");

        // notification queue should be empty — nothing was sent
        NotificationMessage notification = receiveNotificationEvent();
        assertThat(notification.getRecipient()).isEmpty();
        assertThat(notification.getSubject()).isEqualTo("Wallet Credited");
    }

    @Test
    @Order(12)
    @DisplayName("publishWalletCreatedEvent still delivers wallet event when user service returns null")
    void walletCreatedEventStillPublishedWhenUserReturnsNull() throws Exception {
        when(userServiceClient.getUserProfile(42L)).thenReturn(null);

        walletEventService.publishWalletCreatedEvent(42L, "NGN");

        Map<String, Object> payload = receiveWalletEvent();
        assertThat(payload.get("event")).isEqualTo("WALLET_CREATED");
    }

}