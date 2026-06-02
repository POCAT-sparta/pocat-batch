package com.rocketcrew.pocatbatch.job.refundretry;

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
public class RefundRetryJobConfig {

    public static final String JOB_NAME = "refundRetryJob";
    public static final String STEP_NAME = "refundRetryStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final RefundRetryTasklet refundRetryTasklet;

    @Bean(name = JOB_NAME)
    public Job refundRetryJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(refundRetryStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step refundRetryStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(refundRetryTasklet, transactionManager)
                .build();
    }
}
