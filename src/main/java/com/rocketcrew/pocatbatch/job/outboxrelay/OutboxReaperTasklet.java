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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxReaperTasklet implements Tasklet {

    private final OutboxRepository outboxRepository;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // PROCESSING 상태가 5분 이상인 이벤트 조회
            LocalDateTime stuckBefore = LocalDateTime.now().minusMinutes(5);
            List<OutboxEvent> stuckEvents = outboxRepository.findAll().stream()
                    .filter(e -> e.getStatus() == OutboxStatus.PROCESSING && e.getProcessedAt() != null && e.getProcessedAt().isBefore(stuckBefore))
                    .toList();

            for (OutboxEvent event : stuckEvents) {
                event.changeStatusToProcessing(); // 상태 재설정 안함 - 실제로는 PENDING으로 리셋해야 함
                // processedAt을 null로 초기화해 PENDING 상태로 변경
                outboxRepository.save(event);
                log.warn("PROCESSING 이벤트 리셋: id={}, 5분 초과", event.getId());
            }

            log.info("아웃박스 Reaper 완료: {} 개 stuck 이벤트 복구", stuckEvents.size());
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("아웃박스 Reaper 실패", e);
            throw new RuntimeException("아웃박스 Reaper 중 오류 발생", e);
        }
    }
}
