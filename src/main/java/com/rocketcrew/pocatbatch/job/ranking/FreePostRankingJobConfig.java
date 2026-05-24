package com.rocketcrew.pocatbatch.job.ranking;

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
public class FreePostRankingJobConfig {

    public static final String JOB_NAME = "freePostRankingJob";
    public static final String STEP_NAME = "freePostRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FreePostRankingTasklet freePostRankingTasklet;

    @Bean(name = JOB_NAME)
    public Job freePostRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(freePostRankingStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step freePostRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(freePostRankingTasklet, transactionManager)
                .build();
    }
}
