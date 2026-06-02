package com.rocketcrew.pocatbatch.domain.auction.ranking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pocat.batch.ranking.auction")
@Data
public class AuctionRankingProperties {

    private int cacheSize = 100;
    private int ttlSeconds = 70;
    private double likeWeight = 1.0;
    private double bidWeight = 2.0;
    private int maxResponseSize = 100;
}
