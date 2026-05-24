package com.rocketcrew.pocatbatch.job.viewcount;

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
public class ViewCountFlushJobConfig {

    public static final String JOB_NAME = "viewCountFlushJob";
    public static final String STEP_NAME = "viewCountFlushStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ViewCountFlushTasklet viewCountFlushTasklet;

    @Bean(name = JOB_NAME)
    public Job viewCountFlushJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(viewCountFlushStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step viewCountFlushStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(viewCountFlushTasklet, transactionManager)
                .build();
    }
}
