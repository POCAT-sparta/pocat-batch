package com.rocketcrew.pocatbatch.job.buyoutrecovery;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BuyoutRecoveryJobConfig {

    public static final String JOB_NAME = "buyoutRecoveryJob";
    public static final String STEP_NAME = "buyoutRecoveryStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BuyoutRecoveryTasklet buyoutRecoveryTasklet;

    @Bean(name = JOB_NAME)
    public Job buyoutRecoveryJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(buyoutRecoveryStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step buyoutRecoveryStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(buyoutRecoveryTasklet, transactionManager)
                .build();
    }
}
