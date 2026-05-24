package com.rocketcrew.pocatbatch.scheduler;

import com.rocketcrew.pocatbatch.job.ranking.FreePostRankingJobConfig;
import com.rocketcrew.pocatbatch.job.viewcount.ViewCountFlushJobConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job freePostRankingJob;
    private final Job viewCountFlushJob;

    // @RequiredArgsConstructor는 @Qualifier 미지원 → 수동 생성자 필수
    public BatchScheduler(
            JobLauncher jobLauncher,
            @Qualifier(FreePostRankingJobConfig.JOB_NAME) Job freePostRankingJob,
            @Qualifier(ViewCountFlushJobConfig.JOB_NAME)  Job viewCountFlushJob) {
        this.jobLauncher        = jobLauncher;
        this.freePostRankingJob = freePostRankingJob;
        this.viewCountFlushJob  = viewCountFlushJob;
    }

    @Scheduled(fixedDelay = 60_000)
    public void runFreePostRanking() {
        launch(freePostRankingJob, "freePostRankingJob");
    }

    @Scheduled(fixedDelay = 60_000)
    public void runViewCountFlush() {
        launch(viewCountFlushJob, "viewCountFlushJob");
    }

    private void launch(Job job, String label) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("ts", System.currentTimeMillis())
                    .toJobParameters();
            JobExecution jobExecution = jobLauncher.run(job, params);
            if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                log.info("{} 실행 완료", label);
            } else {
                log.error("{} 비정상 종료: status={}, failures={}",
                        label,
                        jobExecution.getStatus(),
                        jobExecution.getAllFailureExceptions());
            }
        } catch (Exception e) {
            log.error("{} 실행 실패", label, e);
        }
    }
}
