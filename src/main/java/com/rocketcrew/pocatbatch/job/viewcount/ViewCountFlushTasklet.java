package com.rocketcrew.pocatbatch.job.viewcount;

import com.rocketcrew.pocatbatch.domain.viewcount.repository.ViewCountBulkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
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
    private static final String TRADE_BUFFER_KEY            = "{view:trade}:buffer";
    private static final String TRADE_PROCESSING_KEY        = "{view:trade}:buffer:processing";

    // 키 표준화 이전(un-tagged) 버퍼 — 1회성 drain 대상. 안정화 후 후속 PR에서 제거.
    private static final String LEGACY_FREE_BUFFER_KEY    = "view:free:buffer";
    private static final String LEGACY_COMMENT_BUFFER_KEY = "comment:free:buffer";
    private static final String LEGACY_TRADE_BUFFER_KEY   = "view:buffer";

    private final StringRedisTemplate redisTemplate;
    private final ViewCountBulkRepository bulkRepository;

    enum FlushType { FREE_VIEW, FREE_COMMENT, TRADE_VIEW }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        runSafely("drainLegacyKeys", this::drainLegacyKeys);
        runSafely("flushFreeView",    () -> flushBuffer(FREE_PROCESSING_KEY,         FREE_BUFFER_KEY,         FlushType.FREE_VIEW));
        runSafely("flushFreeComment", () -> flushBuffer(FREE_COMMENT_PROCESSING_KEY, FREE_COMMENT_BUFFER_KEY, FlushType.FREE_COMMENT));
        runSafely("flushTradeView",   () -> flushBuffer(TRADE_PROCESSING_KEY,        TRADE_BUFFER_KEY,        FlushType.TRADE_VIEW));
        return RepeatStatus.FINISHED;
    }

    private void runSafely(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("{} 실패", label, e);
        }
    }

    private void drainLegacyKeys() {
        drainLegacyKey(LEGACY_FREE_BUFFER_KEY,    FREE_BUFFER_KEY);
        drainLegacyKey(LEGACY_COMMENT_BUFFER_KEY, FREE_COMMENT_BUFFER_KEY);
        drainLegacyKey(LEGACY_TRADE_BUFFER_KEY,   TRADE_BUFFER_KEY);
    }

    // package-private: 단위 테스트 대상. 클러스터에서 un-tagged 키는 rename 불가 → read+merge+delete.
    void drainLegacyKey(String oldKey, String newKey) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(oldKey))) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(oldKey, 0, -1);
        if (entries != null) {
            for (ZSetOperations.TypedTuple<String> entry : entries) {
                if (entry.getValue() != null && entry.getScore() != null) {
                    redisTemplate.opsForZSet().incrementScore(newKey, entry.getValue(), entry.getScore());
                }
            }
        }
        redisTemplate.delete(oldKey);
        log.info("legacy 버퍼 drain 완료: {} -> {}", oldKey, newKey);
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

    // package-private: 단위 테스트 대상.
    void flushKey(String key, FlushType type) {
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

        if (entries == null || entries.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        Map<Long, Integer> deltas = collectDeltas(entries);

        if (deltas.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        try {
            bulkApply(type, deltas);
        } catch (Exception e) {
            String failedKey = key + ":failed";
            log.error("bulk flush 실패, :failed로 이관: key={}, size={}", key, deltas.size(), e);
            for (ZSetOperations.TypedTuple<String> entry : entries) {
                if (entry.getValue() != null && entry.getScore() != null) {
                    redisTemplate.opsForZSet().incrementScore(failedKey, entry.getValue(), entry.getScore());
                }
            }
            redisTemplate.expire(failedKey, 24, TimeUnit.HOURS);
        }

        redisTemplate.delete(key);
    }

    private void bulkApply(FlushType type, Map<Long, Integer> deltas) {
        switch (type) {
            case FREE_VIEW    -> bulkRepository.increaseFreePostViewCount(deltas);
            case FREE_COMMENT -> bulkRepository.increaseFreePostCommentCount(deltas);
            case TRADE_VIEW   -> bulkRepository.increaseTradePostViewCount(deltas);
        }
    }

    // package-private static: 순수 변환 — 단위 테스트 대상.
    static Map<Long, Integer> collectDeltas(Set<ZSetOperations.TypedTuple<String>> entries) {
        Map<Long, Integer> deltas = new HashMap<>();
        for (ZSetOperations.TypedTuple<String> entry : entries) {
            if (entry.getValue() == null || entry.getScore() == null) {
                continue;
            }
            try {
                Long postId = Long.parseLong(entry.getValue());
                int count = (int) Math.round(entry.getScore());
                deltas.put(postId, count);
            } catch (NumberFormatException e) {
                // 유효하지 않은 멤버는 스킵
            }
        }
        return deltas;
    }
}
