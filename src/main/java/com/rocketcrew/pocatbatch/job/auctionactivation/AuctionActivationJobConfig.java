package com.rocketcrew.pocatbatch.job.auctionactivation;

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
public class AuctionActivationJobConfig {

    public static final String JOB_NAME = "auctionActivationJob";
    public static final String STEP_NAME = "auctionActivationStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AuctionActivationTasklet auctionActivationTasklet;

    @Bean(name = JOB_NAME)
    public Job auctionActivationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(auctionActivationStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step auctionActivationStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(auctionActivationTasklet, transactionManager)
                .build();
    }
}
