package com.rocketcrew.pocatbatch.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.util.List;

@Profile("!test")
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.cluster.nodes}")
    private List<String> clusterNodes;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redisson Client Bean
     * 경매 락(auction activation, expiration) 및 일반적인 Redis 분산 락 사용
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        var clusterConfig = config.useClusterServers()
                .addNodeAddress(clusterNodes.stream()
                        .map(node -> "redis://" + node)
                        .toArray(String[]::new));

        if (StringUtils.hasText(redisPassword)) {
            clusterConfig.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}
