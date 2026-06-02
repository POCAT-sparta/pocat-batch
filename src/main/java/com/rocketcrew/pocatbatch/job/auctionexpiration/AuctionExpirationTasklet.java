package com.rocketcrew.pocatbatch.job.auctionexpiration;

import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocatbatch.domain.auction.service.AuctionBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpirationTasklet implements Tasklet {

    private final AuctionRepository auctionRepository;
    private final AuctionBatchService auctionBatchService;
    private final RedissonClient redissonClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Auction> expiredAuctions = auctionRepository.findAllByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(
                    AuctionStatus.ACTIVE, now
            );

            int expiredCount = 0;

            for (Auction auction : expiredAuctions) {
                RLock lock = redissonClient.getLock("auction:lock:" + auction.getId());
                boolean locked;
                try {
                    locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("경매 종료 락 획득 중 인터럽트: auctionId={}", auction.getId());
                    throw new RuntimeException("경매 종료 인터럽트 발생", ie);
                }
                if (!locked) {
                    log.debug("경매 종료 락 실패 (이미 처리 중): auctionId={}", auction.getId());
                    continue;
                }

                try {
                    auctionBatchService.endAuction(auction);
                    expiredCount++;
                } catch (Exception e) {
                    log.error("경매 종료 실패: auctionId={}", auction.getId(), e);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            log.info("경매 종료 완료: {} 개 경매 종료됨", expiredCount);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("경매 종료 작업 실패", e);
            throw new RuntimeException("경매 종료 중 오류 발생", e);
        }
    }
}
