package com.rocketcrew.pocatbatch.job.aireindex;

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
public class AiReindexJobConfig {

    public static final String JOB_NAME = "aiReindexJob";
    public static final String STEP_NAME = "aiReindexStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AiReindexTasklet aiReindexTasklet;

    @Bean(name = JOB_NAME)
    public Job aiReindexJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(aiReindexStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step aiReindexStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(aiReindexTasklet, transactionManager)
                .build();
    }
}
