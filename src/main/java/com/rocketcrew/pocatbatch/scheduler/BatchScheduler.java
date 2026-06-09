package com.rocketcrew.pocatbatch.scheduler;

import com.rocketcrew.pocatbatch.job.aisession.AiSessionCleanupJobConfig;
import com.rocketcrew.pocatbatch.job.auctionactivation.AuctionActivationJobConfig;
import com.rocketcrew.pocatbatch.job.auctionexpiration.AuctionExpirationJobConfig;
import com.rocketcrew.pocatbatch.job.auctionranking.AuctionRankingJobConfig;
import com.rocketcrew.pocatbatch.job.buyoutrecovery.BuyoutRecoveryJobConfig;
import com.rocketcrew.pocatbatch.job.cardsync.CardSyncJobConfig;
import com.rocketcrew.pocatbatch.job.ordercompletion.OrderCompletionJobConfig;
import com.rocketcrew.pocatbatch.job.outboxrelay.OutboxRelayJobConfig;
import com.rocketcrew.pocatbatch.job.ranking.FreePostRankingJobConfig;
import com.rocketcrew.pocatbatch.job.refundretry.RefundRetryJobConfig;
import com.rocketcrew.pocatbatch.job.viewcount.ViewCountFlushJobConfig;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "pocat.batch.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job freePostRankingJob;
    private final Job viewCountFlushJob;
    private final Job aiSessionCleanupJob;
    private final Job auctionRankingJob;
    private final Job outboxRelayJob;
    private final Job cardSyncJob;
    private final Job auctionActivationJob;
    private final Job auctionExpirationJob;
    private final Job buyoutRecoveryJob;
    private final Job refundRetryJob;
    private final Job orderCompletionJob;

    // @RequiredArgsConstructor는 @Qualifier 미지원 → 수동 생성자 필수
    public BatchScheduler(
            JobLauncher jobLauncher,
            @Qualifier(FreePostRankingJobConfig.JOB_NAME) Job freePostRankingJob,
            @Qualifier(ViewCountFlushJobConfig.JOB_NAME) Job viewCountFlushJob,
            @Qualifier(AiSessionCleanupJobConfig.JOB_NAME) Job aiSessionCleanupJob,
            @Qualifier(AuctionRankingJobConfig.JOB_NAME) Job auctionRankingJob,
            @Qualifier(OutboxRelayJobConfig.JOB_NAME) Job outboxRelayJob,
            @Qualifier(CardSyncJobConfig.JOB_NAME) Job cardSyncJob,
            @Qualifier(AuctionActivationJobConfig.JOB_NAME) Job auctionActivationJob,
            @Qualifier(AuctionExpirationJobConfig.JOB_NAME) Job auctionExpirationJob,
            @Qualifier(BuyoutRecoveryJobConfig.JOB_NAME) Job buyoutRecoveryJob,
            @Qualifier(RefundRetryJobConfig.JOB_NAME) Job refundRetryJob,
            @Qualifier(OrderCompletionJobConfig.JOB_NAME) Job orderCompletionJob) {
        this.jobLauncher           = jobLauncher;
        this.freePostRankingJob    = freePostRankingJob;
        this.viewCountFlushJob     = viewCountFlushJob;
        this.aiSessionCleanupJob   = aiSessionCleanupJob;
        this.auctionRankingJob     = auctionRankingJob;
        this.outboxRelayJob        = outboxRelayJob;
        this.cardSyncJob           = cardSyncJob;
        this.auctionActivationJob  = auctionActivationJob;
        this.auctionExpirationJob  = auctionExpirationJob;
        this.buyoutRecoveryJob     = buyoutRecoveryJob;
        this.refundRetryJob        = refundRetryJob;
        this.orderCompletionJob    = orderCompletionJob;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "freePostRankingJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void runFreePostRanking() {
        launch(freePostRankingJob, "freePostRankingJob");
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "viewCountFlushJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void runViewCountFlush() {
        launch(viewCountFlushJob, "viewCountFlushJob");
    }

    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = "aiSessionCleanupJob", lockAtMostFor = "PT4M", lockAtLeastFor = "PT10S")
    public void runAiSessionCleanup() {
        launch(aiSessionCleanupJob, "aiSessionCleanupJob");
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "auctionRankingJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void runAuctionRanking() {
        launch(auctionRankingJob, "auctionRankingJob");
    }

    @Scheduled(fixedDelay = 5_000)
    @SchedulerLock(name = "outboxRelayJob", lockAtMostFor = "PT4S", lockAtLeastFor = "PT1S")
    public void runOutboxRelay() {
        launch(outboxRelayJob, "outboxRelayJob");
    }

    @Scheduled(cron = "0 0 0 * * SUN", zone = "Asia/Seoul")
    @SchedulerLock(name = "cardSyncJob", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void runCardSync() {
        launch(cardSyncJob, "cardSyncJob");
    }

    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "auctionActivationJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void runAuctionActivation() {
        launch(auctionActivationJob, "auctionActivationJob");
    }

    @Scheduled(cron = "0 5-30 19 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "auctionExpirationJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void runAuctionExpiration() {
        launch(auctionExpirationJob, "auctionExpirationJob");
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "buyoutRecoveryJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void runBuyoutRecovery() {
        launch(buyoutRecoveryJob, "buyoutRecoveryJob");
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "refundRetryJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void runRefundRetry() {
        launch(refundRetryJob, "refundRetryJob");
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runOrderCompletion() {
        launch(orderCompletionJob, "orderCompletionJob");
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
