package com.gatepay.walletservice.service;


import com.gatepay.walletservice.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${wallet.idempotency.ttl-hours:24}")
    private long ttlHours;

    private static final String PREFIX = "wallet:idempotency:";

    private String buildKey(String userId, String idempotencyKey) {
        return PREFIX + userId + ":" + idempotencyKey;
    }

    @Override
    public Optional<String> getProcessedResponse(String userId, String idempotencyKey) {
        String key = buildKey(userId, idempotencyKey);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.info("Idempotency hit for key: {}", key);
        }
        return Optional.ofNullable(cached);
    }

    @Override
    public void storeResponse(String userId, String idempotencyKey, String responseJson) {
        String key = buildKey(userId, idempotencyKey);
        redisTemplate.opsForValue().set(key, responseJson, ttlHours, TimeUnit.HOURS);
        log.info("Stored idempotency key: {} with TTL: {} hours", key, ttlHours);
    }

    @Override
    public boolean isAlreadyProcessed(String userId, String idempotencyKey) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(buildKey(userId, idempotencyKey))
        );
    }

    @Override
    public void invalidate(String userId, String idempotencyKey) {
        String key = buildKey(userId, idempotencyKey);
        redisTemplate.delete(key);
        log.info("Invalidated idempotency key: {}", key);
    }
}