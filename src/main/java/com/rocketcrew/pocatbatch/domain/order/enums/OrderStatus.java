package com.rocketcrew.pocatbatch.domain.order.enums;

public enum OrderStatus {
    PAYMENT_PENDING,
    DIRECT_PAYMENT_FAILED,
    AUTO_PAYMENT_FAILED,
    CANCELLED,
    PAYMENT_COMPLETED,
    ORDER_COMPLETED,
    REFUNDED
}
