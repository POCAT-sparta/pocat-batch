package com.rocketcrew.pocatbatch.job.auctionexpiration;

import com.rocketcrew.pocatbatch.client.MainAuctionLifecycleClient;
import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpirationTasklet implements Tasklet {

    private final AuctionRepository auctionRepository;
    private final MainAuctionLifecycleClient mainAuctionLifecycleClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecution().getId();

        LocalDateTime now = LocalDateTime.now();
        List<Auction> expiredAuctions = auctionRepository.findAllByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(
                AuctionStatus.ACTIVE, now
        );

        if (expiredAuctions.isEmpty()) {
            log.info("경매 종료 대상 없음");
            return RepeatStatus.FINISHED;
        }

        int closedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Auction auction : expiredAuctions) {
            try {
                boolean closed = mainAuctionLifecycleClient.closeExpired(auction.getId(), jobExecutionId);
                if (closed) {
                    closedCount++;
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                failedCount++;
                log.error("경매 종료 실패: auctionId={}", auction.getId(), e);
            }
        }

        log.info("경매 종료 완료: 대상={}, 종료={}, 스킵={}, 실패={}",
                expiredAuctions.size(), closedCount, skippedCount, failedCount);
        if (failedCount > 0) {
            throw new RuntimeException("경매 종료 중 " + failedCount + "건 실패 — 로그 확인 필요");
        }
        return RepeatStatus.FINISHED;
    }
}
