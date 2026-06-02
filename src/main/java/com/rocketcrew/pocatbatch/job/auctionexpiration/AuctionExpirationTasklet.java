package com.rocketcrew.pocatbatch.job.auctionexpiration;

import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocatbatch.domain.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpirationTasklet implements Tasklet {

    private final AuctionRepository auctionRepository;
    private final OutboxEventWriter outboxEventWriter;
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
                boolean locked = lock.tryLock();
                if (!locked) {
                    log.debug("경매 종료 락 실패 (이미 처리 중): auctionId={}", auction.getId());
                    continue;
                }

                try {
                    endAuction(auction);
                    expiredCount++;
                } catch (Exception e) {
                    log.error("경매 종료 실패: auctionId={}", auction.getId(), e);
                } finally {
                    lock.unlock();
                }
            }

            log.info("경매 종료 완료: {} 개 경매 종료됨", expiredCount);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("경매 종료 작업 실패", e);
            throw new RuntimeException("경매 종료 중 오류 발생", e);
        }
    }

    @Transactional
    private void endAuction(Auction auction) {
        auction.end();
        auctionRepository.save(auction);

        // Outbox에 이벤트 저장
        String eventPayload = String.format(
                "{\"auctionId\":%d,\"status\":\"ENDED\",\"endedAt\":\"%s\"}",
                auction.getId(), auction.getEndedAt()
        );
        outboxEventWriter.write("auction", String.valueOf(auction.getId()), "auction.ended", eventPayload);
    }
}
