package com.rocketcrew.pocatbatch.job.auctionranking;

import com.rocketcrew.pocatbatch.domain.auction.ranking.service.AuctionRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRankingTasklet implements Tasklet {

    private final AuctionRankingService auctionRankingService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            auctionRankingService.refreshRanking();
            log.info("경매 랭킹 갱신 완료");
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("경매 랭킹 갱신 실패", e);
            throw new RuntimeException("경매 랭킹 갱신 중 오류 발생", e);
        }
    }
}
