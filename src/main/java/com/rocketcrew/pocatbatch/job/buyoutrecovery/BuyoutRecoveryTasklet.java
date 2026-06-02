package com.rocketcrew.pocatbatch.job.buyoutrecovery;

import com.rocketcrew.pocatbatch.client.MainAppBuyoutClient;
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
public class BuyoutRecoveryTasklet implements Tasklet {

    private final AuctionRepository auctionRepository;
    private final MainAppBuyoutClient mainAppBuyoutClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // PAYMENT_PENDING + updatedAt <= now-2분 경매 조회
            LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);
            List<Auction> stuckAuctions = auctionRepository.findAllByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                    AuctionStatus.PAYMENT_PENDING, twoMinutesAgo
            );

            int recoveredCount = 0;
            long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecution().getId();

            for (Auction auction : stuckAuctions) {
                try {
                    mainAppBuyoutClient.recoverBuyout(auction.getId(), jobExecutionId);
                    recoveredCount++;
                } catch (Exception e) {
                    log.warn("구매 확정 복구 실패: auctionId={}", auction.getId(), e);
                }
            }

            log.info("구매 확정 복구 완료: {} 개 경매 복구됨", recoveredCount);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("구매 확정 복구 작업 실패", e);
            throw new RuntimeException("구매 확정 복구 중 오류 발생", e);
        }
    }
}
