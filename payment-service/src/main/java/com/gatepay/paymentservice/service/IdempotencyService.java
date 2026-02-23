package com.gatepay.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final String IN_PROGRESS = "IN_PROGRESS";

    /**
     * Try to acquire an idempotency lock for the given key
     * @param key Unique identifier (usually transaction reference)
     * @param ttl Time to live for the lock
     * @return true if lock acquired, false if already exists
     */
    public boolean tryAcquireLock(String key, Duration ttl) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, IN_PROGRESS, ttl);

        log.debug("Idempotency lock attempt | key={} | acquired={}", key, result);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Save the response for a given key
     * @param key Unique identifier
     * @param response Serialized response to cache
     * @param ttl Time to live
     */
    public void saveResponse(String key, String response, Duration ttl) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, response, ttl);
        log.debug("Idempotency response saved | key={}", key);
    }

    /**
     * Get cached response for a given key
     * @param key Unique identifier
     * @return Optional containing the response if exists
     */
    public Optional<String> getResponse(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        String response = redisTemplate.opsForValue().get(redisKey);
        return Optional.ofNullable(response)
                .filter(r -> !IN_PROGRESS.equals(r));
    }

    /**
     * Check if a request is currently in progress
     * @param key Unique identifier
     * @return true if request is in progress
     */
    public boolean isInProgress(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        String value = redisTemplate.opsForValue().get(redisKey);
        return IN_PROGRESS.equals(value);
    }

    /**
     * Release the idempotency lock (useful for error cases)
     * @param key Unique identifier
     */
    public void releaseLock(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        redisTemplate.delete(redisKey);
        log.debug("Idempotency lock released | key={}", key);
    }

    /**
     * Delete cached response and lock
     * @param key Unique identifier
     */
    public void invalidate(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        redisTemplate.delete(redisKey);
        log.debug("Idempotency key invalidated | key={}", key);
    }
}