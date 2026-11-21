package com.gatepay.paymentservice.model.enums;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED // hit max attempts, needs a manual look
}
