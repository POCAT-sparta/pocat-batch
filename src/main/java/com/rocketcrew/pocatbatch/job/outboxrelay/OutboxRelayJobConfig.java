package com.rocketcrew.pocatbatch.job.outboxrelay;

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
public class OutboxRelayJobConfig {

    public static final String JOB_NAME = "outboxRelayJob";
    public static final String RELAY_STEP_NAME = "outboxRelayStep";
    public static final String REAPER_STEP_NAME = "outboxReaperStep";
    public static final String CLEANUP_STEP_NAME = "outboxCleanupStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OutboxRelayTasklet outboxRelayTasklet;
    private final OutboxReaperTasklet outboxReaperTasklet;
    private final OutboxCleanupTasklet outboxCleanupTasklet;

    @Bean(name = JOB_NAME)
    public Job outboxRelayJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(outboxRelayStep())
                    .on("FAILED").to(outboxReaperStep())
                .from(outboxRelayStep())
                    .on("*").to(outboxReaperStep())
                .from(outboxReaperStep())
                    .on("*").to(outboxCleanupStep())
                .end()
                .build();
    }

    @Bean(name = RELAY_STEP_NAME)
    public Step outboxRelayStep() {
        return new StepBuilder(RELAY_STEP_NAME, jobRepository)
                .tasklet(outboxRelayTasklet, transactionManager)
                .build();
    }

    @Bean(name = REAPER_STEP_NAME)
    public Step outboxReaperStep() {
        return new StepBuilder(REAPER_STEP_NAME, jobRepository)
                .tasklet(outboxReaperTasklet, transactionManager)
                .build();
    }

    @Bean(name = CLEANUP_STEP_NAME)
    public Step outboxCleanupStep() {
        return new StepBuilder(CLEANUP_STEP_NAME, jobRepository)
                .tasklet(outboxCleanupTasklet, transactionManager)
                .build();
    }
}
