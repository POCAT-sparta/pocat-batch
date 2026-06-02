package com.rocketcrew.pocatbatch.job.aisession;

import com.rocketcrew.pocatbatch.domain.ai.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiSessionCleanupTasklet implements Tasklet {

    private final AiChatSessionRepository aiChatSessionRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // 30분 이상 비활성인 세션 만료 처리
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
            int expiredCount = aiChatSessionRepository.expireSessionsBeforeTime(threshold);
            log.info("AI 세션 만료 처리 완료: {}개 세션 만료됨", expiredCount);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("AI 세션 만료 처리 실패", e);
            throw new RuntimeException("AI 세션 만료 처리 중 오류 발생", e);
        }
    }
}
