package com.rocketcrew.pocatbatch.domain.refund.entity;

public enum RefundStatus {
    REQUESTED,
    PROCESSING,
    COMPLETED,
    REJECTED,
    FAILED_RETRYABLE,
    FAILED_FINAL
}
