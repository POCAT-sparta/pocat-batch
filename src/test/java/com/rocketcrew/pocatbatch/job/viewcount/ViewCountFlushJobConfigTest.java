package com.rocketcrew.pocatbatch.job.viewcount;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class ViewCountFlushJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ViewCountFlushJobConfig.JOB_NAME)
    private Job viewCountFlushJob;

    @Test
    void viewCountFlushJob_실행_성공() throws Exception {
        jobLauncherTestUtils.setJob(viewCountFlushJob);

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("ts", System.currentTimeMillis())
                        .toJobParameters());

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
    }
}
