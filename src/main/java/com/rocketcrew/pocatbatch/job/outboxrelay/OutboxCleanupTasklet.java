package com.rocketcrew.pocatbatch.job.outboxrelay;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupTasklet implements Tasklet {

    private final OutboxRepository outboxRepository;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // SENT 상태이고 7일 이상 된 이벤트 삭제
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            int deletedCount = outboxRepository.deleteOldEvents(OutboxStatus.SENT, cutoff);

            log.info("아웃박스 정리 완료: {} 개 오래된 이벤트 삭제", deletedCount);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("아웃박스 정리 실패", e);
            throw new RuntimeException("아웃박스 정리 중 오류 발생", e);
        }
    }
}
