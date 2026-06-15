package com.rocketcrew.pocatbatch.job.cardsync;

import com.rocketcrew.pocatbatch.client.MainCardSyncClient;
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
public class CardSyncTasklet implements Tasklet {

    private final MainCardSyncClient mainCardSyncClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecution().getId();

        log.info("[CardSync] 메인 앱 카드 동기화 트리거 요청 시작: jobExecutionId={}", jobExecutionId);
        mainCardSyncClient.triggerSync(jobExecutionId);
        log.info("[CardSync] 메인 앱 카드 동기화 트리거 요청 완료: jobExecutionId={}", jobExecutionId);

        return RepeatStatus.FINISHED;
    }
}
