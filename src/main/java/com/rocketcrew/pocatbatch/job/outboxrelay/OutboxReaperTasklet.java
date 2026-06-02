package com.rocketcrew.pocatbatch.job.outboxrelay;

import com.rocketcrew.pocatbatch.domain.outbox.entity.OutboxEvent;
import com.rocketcrew.pocatbatch.domain.outbox.enums.OutboxStatus;
import com.rocketcrew.pocatbatch.domain.outbox.repository.OutboxRepository;
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
public class OutboxReaperTasklet implements Tasklet {

    private final OutboxRepository outboxRepository;
    private static final int STUCK_MINUTES = 5;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            LocalDateTime stuckBefore = LocalDateTime.now().minusMinutes(STUCK_MINUTES);

            List<OutboxEvent> stuckEvents = outboxRepository.findTop100ByStatusAndProcessedAtBefore(
                    OutboxStatus.PROCESSING, stuckBefore
            );

            for (OutboxEvent event : stuckEvents) {
                try {
                    event.resetToPending();
                    outboxRepository.save(event);
                    log.warn("Outbox stuck 이벤트 PENDING 복구: id={}", event.getId());
                } catch (Exception e) {
                    log.error("Outbox stuck 이벤트 복구 실패: id={}, error={}", event.getId(), e.getMessage());
                }
            }

            log.info("OutboxReaper 완료: {} 개 복구", stuckEvents.size());
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("아웃박스 Reaper 실패", e);
            throw new RuntimeException("아웃박스 Reaper 중 오류 발생", e);
        }
    }
}
