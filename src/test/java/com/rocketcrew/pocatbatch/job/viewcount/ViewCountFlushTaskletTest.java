package com.rocketcrew.pocatbatch.job.viewcount;

import com.rocketcrew.pocatbatch.domain.viewcount.repository.ViewCountBulkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewCountFlushTaskletTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private ViewCountBulkRepository bulkRepository;

    @InjectMocks
    private ViewCountFlushTasklet tasklet;

    private static ZSetOperations.TypedTuple<String> tuple(String value, double score) {
        return new DefaultTypedTuple<>(value, score);
    }

    @Test
    void collectDeltas_파싱_반올림_유효하지않은엔트리_스킵() {
        Set<ZSetOperations.TypedTuple<String>> entries = new LinkedHashSet<>();
        entries.add(tuple("100", 3.0));
        entries.add(tuple("200", 2.6));     // 반올림 → 3
        entries.add(tuple(null, 5.0));      // 스킵
        entries.add(tuple("notNumber", 1)); // 스킵

        Map<Long, Integer> deltas = ViewCountFlushTasklet.collectDeltas(entries);

        assertThat(deltas).containsEntry(100L, 3).containsEntry(200L, 3);
        assertThat(deltas).hasSize(2);
    }

    @Test
    void flushKey_성공시_bulk호출_및_키삭제() {
        String key = "{view:trade}:buffer:processing";
        Set<ZSetOperations.TypedTuple<String>> entries = new LinkedHashSet<>();
        entries.add(tuple("100", 3.0));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(key, 0, -1)).thenReturn(entries);

        tasklet.flushKey(key, ViewCountFlushTasklet.FlushType.TRADE_VIEW);

        verify(bulkRepository).increaseTradePostViewCount(Map.of(100L, 3));
        verify(redisTemplate).delete(key);
        verify(zSetOps, never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    void flushKey_bulk실패시_failed로_이관() {
        String key = "{view:free}:buffer:processing";
        String failedKey = key + ":failed";
        Set<ZSetOperations.TypedTuple<String>> entries = new LinkedHashSet<>();
        entries.add(tuple("100", 3.0));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(key, 0, -1)).thenReturn(entries);
        doThrow(new RuntimeException("db down"))
                .when(bulkRepository).increaseFreePostViewCount(any());

        tasklet.flushKey(key, ViewCountFlushTasklet.FlushType.FREE_VIEW);

        verify(zSetOps).incrementScore(failedKey, "100", 3.0);
        verify(redisTemplate).expire(failedKey, 24, TimeUnit.HOURS);
        verify(redisTemplate).delete(key);
    }

    @Test
    void drainLegacyKey_구키존재시_신규키로_병합후_삭제() {
        String oldKey = "view:buffer";
        String newKey = "{view:trade}:buffer";
        Set<ZSetOperations.TypedTuple<String>> entries = new LinkedHashSet<>();
        entries.add(tuple("100", 5.0));
        when(redisTemplate.hasKey(oldKey)).thenReturn(true);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(oldKey, 0, -1)).thenReturn(entries);

        tasklet.drainLegacyKey(oldKey, newKey);

        verify(zSetOps).incrementScore(newKey, "100", 5.0);
        verify(redisTemplate).delete(oldKey);
    }

    @Test
    void drainLegacyKey_구키없으면_아무것도안함() {
        when(redisTemplate.hasKey("view:buffer")).thenReturn(false);

        tasklet.drainLegacyKey("view:buffer", "{view:trade}:buffer");

        verify(redisTemplate, never()).delete(anyString());
    }
}
