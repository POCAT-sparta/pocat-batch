package com.rocketcrew.pocatbatch.job.viewcount;

import com.rocketcrew.pocatbatch.domain.freepost.service.FreePostFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushTasklet implements Tasklet {

    private static final String FREE_BUFFER_KEY             = "{view:free}:buffer";
    private static final String FREE_PROCESSING_KEY         = "{view:free}:buffer:processing";
    private static final String FREE_COMMENT_BUFFER_KEY     = "{comment:free}:buffer";
    private static final String FREE_COMMENT_PROCESSING_KEY = "{comment:free}:buffer:processing";

    private final StringRedisTemplate redisTemplate;
    private final FreePostFlushService flushService;

    private enum FlushType { FREE_VIEW, FREE_COMMENT }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        runSafely("flushFreeView",    () -> flushBuffer(FREE_PROCESSING_KEY,         FREE_BUFFER_KEY,         FlushType.FREE_VIEW));
        runSafely("flushFreeComment", () -> flushBuffer(FREE_COMMENT_PROCESSING_KEY, FREE_COMMENT_BUFFER_KEY, FlushType.FREE_COMMENT));
        return RepeatStatus.FINISHED;
    }

    private void runSafely(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("{} 실패", label, e);
        }
    }

    private void flushBuffer(String processingKey, String bufferKey, FlushType type) {
        String failedKey = processingKey + ":failed";

        if (Boolean.TRUE.equals(redisTemplate.hasKey(failedKey))) {
            log.warn("이전 실패 항목 발견, 버퍼 재병합: key={}", failedKey);
            Set<ZSetOperations.TypedTuple<String>> failedEntries =
                    redisTemplate.opsForZSet().rangeWithScores(failedKey, 0, -1);
            if (failedEntries != null) {
                for (ZSetOperations.TypedTuple<String> entry : failedEntries) {
                    redisTemplate.opsForZSet().incrementScore(bufferKey, entry.getValue(), entry.getScore());
                }
            }
            redisTemplate.delete(failedKey);
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey(processingKey))) {
            log.warn("미처리 데이터 발견, DB 업데이트 재시도: key={}", processingKey);
            flushKey(processingKey, type);
        }

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(bufferKey))) {
            return;
        }

        redisTemplate.rename(bufferKey, processingKey);
        flushKey(processingKey, type);
    }

    private void flushKey(String key, FlushType type) {
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

        if (entries == null || entries.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        String failedKey = key + ":failed";

        for (ZSetOperations.TypedTuple<String> entry : entries) {
            if (entry.getValue() == null || entry.getScore() == null) {
                log.warn("유효하지 않은 ZSet 엔트리 스킵: key={}", key);
                continue;
            }
            try {
                Long postId = Long.parseLong(entry.getValue());
                int count = (int) Math.round(entry.getScore());
                switch (type) {
                    case FREE_VIEW    -> flushService.increaseViewCount(postId, count);
                    case FREE_COMMENT -> flushService.updateCommentCount(postId, count);
                }
            } catch (Exception e) {
                log.error("flush 실패: entry={}, key={}", entry.getValue(), key, e);
                redisTemplate.opsForZSet().incrementScore(failedKey, entry.getValue(), entry.getScore());
                redisTemplate.expire(failedKey, 24, TimeUnit.HOURS);
            }
        }

        redisTemplate.delete(key);
    }
}
