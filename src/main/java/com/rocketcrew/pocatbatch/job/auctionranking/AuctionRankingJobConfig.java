package com.rocketcrew.pocatbatch.job.auctionranking;

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
public class AuctionRankingJobConfig {

    public static final String JOB_NAME = "auctionRankingJob";
    public static final String STEP_NAME = "auctionRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AuctionRankingTasklet auctionRankingTasklet;

    @Bean(name = JOB_NAME)
    public Job auctionRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(auctionRankingStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step auctionRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(auctionRankingTasklet, transactionManager)
                .build();
    }
}
