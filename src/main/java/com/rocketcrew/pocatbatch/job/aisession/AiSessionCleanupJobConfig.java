package com.rocketcrew.pocatbatch.job.aisession;

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
public class AiSessionCleanupJobConfig {

    public static final String JOB_NAME = "aiSessionCleanupJob";
    public static final String STEP_NAME = "aiSessionCleanupStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AiSessionCleanupTasklet aiSessionCleanupTasklet;

    @Bean(name = JOB_NAME)
    public Job aiSessionCleanupJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(aiSessionCleanupStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step aiSessionCleanupStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(aiSessionCleanupTasklet, transactionManager)
                .build();
    }
}
