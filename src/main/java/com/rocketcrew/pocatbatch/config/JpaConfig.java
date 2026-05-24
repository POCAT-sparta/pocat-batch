package com.rocketcrew.pocatbatch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@Configuration
@EnableJpaAuditing
@EntityScan(basePackages = "com.rocketcrew.pocatbatch.domain")
@EnableJpaRepositories(basePackages = "com.rocketcrew.pocatbatch.domain")
public class JpaConfig {
}
