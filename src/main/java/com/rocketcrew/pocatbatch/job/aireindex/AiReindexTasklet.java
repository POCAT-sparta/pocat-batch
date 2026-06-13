package com.rocketcrew.pocatbatch.job.aireindex;

import com.rocketcrew.pocatbatch.client.MainAiReindexClient;
import com.rocketcrew.pocatbatch.client.dto.ReindexChunkResponse;
import com.rocketcrew.pocatbatch.domain.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReindexTasklet implements Tasklet {

    private static final int CHUNK_SIZE = 100;

    private final CardRepository cardRepository;
    private final MainAiReindexClient mainAiReindexClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecution().getId();

        long lastId = 0L;
        int totalIndexed = 0;
        int totalProcessed = 0;

        while (true) {
            List<Long> cardIds = cardRepository.findActiveCardIdsAfter(lastId, PageRequest.of(0, CHUNK_SIZE));

            if (cardIds.isEmpty()) {
                log.info("AI 카드 리인덱스 종료: 더 이상 처리할 카드 없음. lastId={}", lastId);
                break;
            }

            try {
                ReindexChunkResponse response = mainAiReindexClient.reindexChunk(cardIds, jobExecutionId);

                totalProcessed += response.processedCount();
                totalIndexed += response.indexedCount();

                if (response.rateLimited()) {
                    log.warn("AI 카드 리인덱스 rate limit 감지: 작업을 조기 종료합니다. lastId={}", lastId);
                    break;
                }
            } catch (Exception e) {
                log.warn("AI 카드 리인덱스 청크 처리 실패: cardIds={}", cardIds, e);
            }

            lastId = cardIds.get(cardIds.size() - 1);
        }

        log.info("AI 카드 리인덱스 완료: totalProcessed={}, totalIndexed={}", totalProcessed, totalIndexed);
        return RepeatStatus.FINISHED;
    }
}
