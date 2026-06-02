package com.rocketcrew.pocatbatch.job.cardsync;

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
public class CardSyncJobConfig {

    public static final String JOB_NAME = "cardSyncJob";
    public static final String STEP_NAME = "cardSyncStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CardSyncTasklet cardSyncTasklet;

    @Bean(name = JOB_NAME)
    public Job cardSyncJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(cardSyncStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step cardSyncStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(cardSyncTasklet, transactionManager)
                .build();
    }
}
