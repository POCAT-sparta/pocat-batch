package com.rocketcrew.pocatbatch.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ECS 롤링 디플로이 시 신/구 태스크가 잠시 동시에 떠 있는 구간에서도
 * 스케줄러 잡이 중복 실행되지 않도록 Redis 기반 분산 락(ShedLock)을 적용한다.
 */
@Profile("!test")
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "pocat-batch");
    }
}
