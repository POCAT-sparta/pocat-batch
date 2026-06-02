package com.rocketcrew.pocatbatch.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redisson Client Bean
     * 경매 락(auction activation, expiration) 및 일반적인 Redis 분산 락 사용
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String redisUrl = (redisPassword == null || redisPassword.isEmpty())
                ? String.format("redis://%s:%d", redisHost, redisPort)
                : String.format("redis://:%s@%s:%d", redisPassword, redisHost, redisPort);
        config.useSingleServer().setAddress(redisUrl);
        return Redisson.create(config);
    }
}
