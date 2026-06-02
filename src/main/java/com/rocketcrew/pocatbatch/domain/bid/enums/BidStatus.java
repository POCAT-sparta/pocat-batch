package com.rocketcrew.pocatbatch.domain.bid.enums;

public enum BidStatus {
    LEADING, // 현재 최고 입찰
    OUTBID,  // 다른 입찰자에게 밀림
    WON,     // 최종 낙찰
    LOST,    // 최종 패찰
    CANCELLED
}
