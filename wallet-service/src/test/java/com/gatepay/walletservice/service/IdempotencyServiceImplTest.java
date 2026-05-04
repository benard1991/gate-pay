package com.gatepay.walletservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest //doesn't do a full component scan, so your service wouldn't be found automatically. it will not boots everything — JPA, Flyway, web layer
@Import(IdempotencyServiceImpl.class)  // bring in the service manually
@Testcontainers
class IdempotencyServiceImplIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String USER_ID = "user-123";
    private static final String IDEMPOTENCY_KEY = "txn-abc-456";
    private static final String RESPONSE_JSON = "{\"status\":\"SUCCESS\",\"transactionId\":\"txn-abc-456\"}";

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void storingAResponseMakesItRetrievableByTheSameUserAndKey() {
        idempotencyService.storeResponse(USER_ID, IDEMPOTENCY_KEY, RESPONSE_JSON);

        Optional<String> result = idempotencyService.getProcessedResponse(USER_ID, IDEMPOTENCY_KEY);

        assertThat(result).isPresent().contains(RESPONSE_JSON);
    }

    @Test
    void lookingUpAKeyThatWasNeverStoredReturnsEmpty() {
        Optional<String> result = idempotencyService.getProcessedResponse(USER_ID, "non-existent-key");

        assertThat(result).isEmpty();
    }

    @Test
    void requestIsMarkedAsAlreadyProcessedAfterStoringItsResponse() {
        idempotencyService.storeResponse(USER_ID, IDEMPOTENCY_KEY, RESPONSE_JSON);

        boolean alreadyProcessed = idempotencyService.isAlreadyProcessed(USER_ID, IDEMPOTENCY_KEY);

        assertThat(alreadyProcessed).isTrue();
    }

    @Test
    void requestIsNotMarkedAsProcessedBeforeAnyResponseIsStored() {
        boolean alreadyProcessed = idempotencyService.isAlreadyProcessed(USER_ID, IDEMPOTENCY_KEY);

        assertThat(alreadyProcessed).isFalse();
    }

    @Test
    void invalidatingAKeyRemovesItFromTheStore() {
        idempotencyService.storeResponse(USER_ID, IDEMPOTENCY_KEY, RESPONSE_JSON);

        idempotencyService.invalidate(USER_ID, IDEMPOTENCY_KEY);

        assertThat(idempotencyService.isAlreadyProcessed(USER_ID, IDEMPOTENCY_KEY)).isFalse();
        assertThat(idempotencyService.getProcessedResponse(USER_ID, IDEMPOTENCY_KEY)).isEmpty();
    }

    @Test
    void differentUsersWithTheSameIdempotencyKeyAreStoredIndependently() {
        String otherUserId = "user-999";
        String otherResponseJson = "{\"status\":\"PENDING\"}";

        idempotencyService.storeResponse(USER_ID, IDEMPOTENCY_KEY, RESPONSE_JSON);
        idempotencyService.storeResponse(otherUserId, IDEMPOTENCY_KEY, otherResponseJson);

        assertThat(idempotencyService.getProcessedResponse(USER_ID, IDEMPOTENCY_KEY))
                .contains(RESPONSE_JSON);
        assertThat(idempotencyService.getProcessedResponse(otherUserId, IDEMPOTENCY_KEY))
                .contains(otherResponseJson);
    }

    @Test
    void storingANewResponseForTheSameKeyOverwritesThePreviousOne() {
        String updatedResponse = "{\"status\":\"FAILED\"}";

        idempotencyService.storeResponse(USER_ID, IDEMPOTENCY_KEY, RESPONSE_JSON);
        idempotencyService.storeResponse(USER_ID, IDEMPOTENCY_KEY, updatedResponse);

        assertThat(idempotencyService.getProcessedResponse(USER_ID, IDEMPOTENCY_KEY))
                .contains(updatedResponse);
    }

    @Test
    void invalidatingAKeyThatDoesNotExistDoesNotThrow() {
        // should be a no-op, not an exception
        idempotencyService.invalidate(USER_ID, "ghost-key");

        assertThat(idempotencyService.isAlreadyProcessed(USER_ID, "ghost-key")).isFalse();
    }
}