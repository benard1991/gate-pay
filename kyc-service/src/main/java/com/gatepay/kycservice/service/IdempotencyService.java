package com.gatepay.kycservice.service;

public interface IdempotencyService {

    void checkAndLock(String key);

    void clear(String key);
}
