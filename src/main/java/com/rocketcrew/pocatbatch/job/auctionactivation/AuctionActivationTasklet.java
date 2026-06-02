package com.rocketcrew.pocatbatch.job.auctionactivation;

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
public class AuctionActivationTasklet implements Tasklet {

    private final AuctionRepository auctionRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final RedissonClient redissonClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            List<Auction> approvedAuctions = auctionRepository.findAllByStatus(AuctionStatus.APPROVED);
            int activatedCount = 0;
            long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecution().getId();

            for (Auction auction : approvedAuctions) {
                RLock lock = redissonClient.getLock("auction:lock:" + auction.getId());
                boolean locked = lock.tryLock();
                if (!locked) {
                    log.debug("경매 활성화 락 실패 (이미 처리 중): auctionId={}", auction.getId());
                    continue;
                }

                try {
                    activateAuction(auction);
                    activatedCount++;
                } catch (Exception e) {
                    log.error("경매 활성화 실패: auctionId={}", auction.getId(), e);
                } finally {
                    lock.unlock();
                }
            }

            log.info("경매 활성화 완료: {} 개 경매 활성화됨", activatedCount);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("경매 활성화 작업 실패", e);
            throw new RuntimeException("경매 활성화 중 오류 발생", e);
        }
    }

    @Transactional
    private void activateAuction(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = now;
        LocalDateTime endedAt = now.plusHours(7); // 기본 7시간 경매

        auction.activate(startedAt, endedAt);
        auctionRepository.save(auction);

        // Outbox에 이벤트 저장
        String eventPayload = String.format(
                "{\"auctionId\":%d,\"status\":\"ACTIVE\",\"startedAt\":\"%s\",\"endedAt\":\"%s\"}",
                auction.getId(), startedAt, endedAt
        );
        outboxEventWriter.write("auction", String.valueOf(auction.getId()), "auction.activated", eventPayload);
    }
}
