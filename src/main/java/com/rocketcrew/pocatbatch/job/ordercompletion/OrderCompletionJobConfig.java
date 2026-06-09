package com.rocketcrew.pocatbatch.job.ordercompletion;

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
public class OrderCompletionJobConfig {

    public static final String JOB_NAME = "orderCompletionJob";
    public static final String STEP_NAME = "orderCompletionStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OrderCompletionTasklet orderCompletionTasklet;

    @Bean(name = JOB_NAME)
    public Job orderCompletionJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(orderCompletionStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step orderCompletionStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(orderCompletionTasklet, transactionManager)
                .build();
    }
}
