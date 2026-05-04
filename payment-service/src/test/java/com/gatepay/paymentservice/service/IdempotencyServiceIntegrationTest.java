package com.gatepay.paymentservice.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.redis.testcontainers.RedisContainer;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("IdempotencyService Integration Tests")
class IdempotencyServiceIntegrationTest {

    @Container
    static RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("test_db")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> "5672");
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @BeforeEach
    void cleanUpRedis() {
        // Start each test with a clean slate so tests don't bleed into each other
        idempotencyService.invalidate("payment-ref-001");
        idempotencyService.invalidate("payment-ref-002");
        idempotencyService.invalidate("payment-ref-003");
    }

    // LOCK TESTS

    @Test
    @DisplayName("should acquire a lock successfully when no lock exists for the key")
    void should_acquire_lock_when_key_is_free() {
        boolean acquired = idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("should not acquire a lock when the key is already locked by another request")
    void should_not_acquire_lock_when_key_is_already_locked() {
        // First request grabs the lock
        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        // Second request with the same key should be rejected
        boolean secondAttempt = idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("should allow a new lock after the previous lock is explicitly released")
    void should_allow_lock_after_release() {
        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        // Simulate the payment finishing and releasing its lock
        idempotencyService.releaseLock("payment-ref-001");

        // A fresh request should now be able to take the lock
        boolean reacquired = idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        assertThat(reacquired).isTrue();
    }

    @Test
    @DisplayName("should treat different payment references as completely independent locks")
    void should_handle_different_keys_independently() {
        boolean firstRef  = idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));
        boolean secondRef = idempotencyService.tryAcquireLock("payment-ref-002", Duration.ofMinutes(5));
        boolean thirdRef  = idempotencyService.tryAcquireLock("payment-ref-003", Duration.ofMinutes(5));

        assertThat(firstRef).isTrue();
        assertThat(secondRef).isTrue();
        assertThat(thirdRef).isTrue();
    }

    // ─────────────────────────────────────────
    // IN-PROGRESS DETECTION TESTS
    // ─────────────────────────────────────────

    @Test
    @DisplayName("should report a payment as in-progress once its lock is acquired")
    void should_detect_payment_as_in_progress_after_lock() {
        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        boolean inProgress = idempotencyService.isInProgress("payment-ref-001");

        assertThat(inProgress).isTrue();
    }

    @Test
    @DisplayName("should not report a payment as in-progress before any lock is taken")
    void should_not_report_in_progress_when_no_lock_exists() {
        boolean inProgress = idempotencyService.isInProgress("payment-ref-001");

        assertThat(inProgress).isFalse();
    }

    @Test
    @DisplayName("should not report in-progress once a completed response has replaced the lock")
    void should_not_report_in_progress_after_response_is_saved() {
        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        // Payment completes — save the response which overwrites the IN_PROGRESS marker
        idempotencyService.saveResponse("payment-ref-001", "{\"success\":true}", Duration.ofMinutes(30));

        boolean inProgress = idempotencyService.isInProgress("payment-ref-001");

        assertThat(inProgress).isFalse();
    }

    // RESPONSE CACHING TESTS

    @Test
    @DisplayName("should store and retrieve a payment response exactly as saved")
    void should_save_and_retrieve_response() {
        String responseJson = "{\"success\":true,\"reference\":\"payment-ref-001\",\"authorizationUrl\":\"https://paystack.com/pay/abc\"}";

        idempotencyService.saveResponse("payment-ref-001", responseJson, Duration.ofMinutes(30));

        Optional<String> retrieved = idempotencyService.getResponse("payment-ref-001");

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(responseJson);
    }

    @Test
    @DisplayName("should return empty when no response has been saved for a reference")
    void should_return_empty_when_no_response_exists() {
        Optional<String> retrieved = idempotencyService.getResponse("payment-ref-001");

        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("should return empty for the IN_PROGRESS marker — only completed responses are returned")
    void should_not_return_in_progress_marker_as_a_response() {
        // A locked-but-not-yet-completed payment stores IN_PROGRESS in Redis
        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        // getResponse should filter out the IN_PROGRESS marker
        Optional<String> retrieved = idempotencyService.getResponse("payment-ref-001");

        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("should correctly overwrite IN_PROGRESS with the actual response when payment completes")
    void should_overwrite_in_progress_with_completed_response() {
        String responseJson = "{\"success\":true,\"reference\":\"payment-ref-001\"}";

        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        // Payment finishes — save the real response
        idempotencyService.saveResponse("payment-ref-001", responseJson, Duration.ofMinutes(30));

        Optional<String> retrieved = idempotencyService.getResponse("payment-ref-001");

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(responseJson);
    }

    // INVALIDATION TESTS

    @Test
    @DisplayName("should completely remove a cached response when invalidated")
    void should_remove_response_on_invalidate() {
        idempotencyService.saveResponse("payment-ref-001", "{\"success\":true}", Duration.ofMinutes(30));

        idempotencyService.invalidate("payment-ref-001");

        Optional<String> retrieved = idempotencyService.getResponse("payment-ref-001");

        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("should completely remove a lock when invalidated")
    void should_remove_lock_on_invalidate() {
        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        idempotencyService.invalidate("payment-ref-001");

        // After invalidation the key is gone — a new request should be able to lock it
        boolean reacquired = idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofMinutes(5));

        assertThat(reacquired).isTrue();
    }

    @Test
    @DisplayName("should not affect other keys when one key is invalidated")
    void should_only_invalidate_the_targeted_key() {
        idempotencyService.saveResponse("payment-ref-001", "{\"success\":true}", Duration.ofMinutes(30));
        idempotencyService.saveResponse("payment-ref-002", "{\"success\":true}", Duration.ofMinutes(30));

        idempotencyService.invalidate("payment-ref-001");

        assertThat(idempotencyService.getResponse("payment-ref-001")).isEmpty();
        assertThat(idempotencyService.getResponse("payment-ref-002")).isPresent();
    }

    // TTL TESTS

    @Test
    @DisplayName("should expire a lock after its TTL elapses — simulating a very short TTL")
    void should_expire_lock_after_ttl() throws InterruptedException {
        // Use a very short TTL so we don't slow the test suite down
        idempotencyService.tryAcquireLock("payment-ref-001", Duration.ofSeconds(1));

        assertThat(idempotencyService.isInProgress("payment-ref-001")).isTrue();

        // Wait for Redis to expire the key
        Thread.sleep(1500);

        assertThat(idempotencyService.isInProgress("payment-ref-001")).isFalse();
    }

    @Test
    @DisplayName("should expire a cached response after its TTL elapses")
    void should_expire_response_after_ttl() throws InterruptedException {
        idempotencyService.saveResponse(
                "payment-ref-001",
                "{\"success\":true}",
                Duration.ofSeconds(1)
        );

        assertThat(idempotencyService.getResponse("payment-ref-001")).isPresent();

        Thread.sleep(1500);

        assertThat(idempotencyService.getResponse("payment-ref-001")).isEmpty();
    }
}