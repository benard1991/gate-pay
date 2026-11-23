package com.gatepay.kycservice.enums;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED // hit max attempts, needs a manual look
}
