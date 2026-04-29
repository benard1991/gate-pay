package com.gatepay.kycservice.service;

import com.gatepay.kycservice.exception.ErrorCode;
import com.gatepay.kycservice.exception.IdempotencyException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService implements IdempotencyService{
    private static final String PREFIX = "kyc:idempotency:";

    private final StringRedisTemplate redisTemplate;

    @Value("${kyc.idempotency.ttl-minutes:5}")
    private long ttlMinutes;

    @Override
    public void checkAndLock(String key) {
        String redisKey = PREFIX + key;

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "LOCKED", Duration.ofMinutes(ttlMinutes));

        if (!Boolean.TRUE.equals(success)) { // ← handles both false AND null
            throw new IdempotencyException(
                    "Duplicate request detected. Please wait before retrying."
            );
        }
    }

    @Override
    public void clear(String key) {
        redisTemplate.delete(PREFIX + key);
    }


}
