# 조회수 Flush — 거래글 추가 & Bulk 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 거래글(tradepost) 조회수 flush를 배치에 추가하고, 자유글 조회수·댓글수까지 모두 JDBC batch(한 번에 처리)로 전환하며, 메인앱 Redis 키를 클러스터 해시태그 컨벤션으로 정합화한다.

**Architecture:** 메인앱은 `{...}` 해시태그가 붙은 ZSet 버퍼에 카운트를 누적하고, 기존 1분 주기 `viewCountFlushJob`이 버퍼를 `processing`으로 `RENAME` 후 ZSet 전량을 `Map<id,delta>`로 모아 `JdbcTemplate.batchUpdate`로 1회 반영한다. 실패분은 `:failed` ZSet에 모아 다음 회차 재병합한다. 키 변경 직후 구 키 backlog는 tasklet이 1회성으로 drain한다.

**Tech Stack:** Spring Batch, Spring Data Redis(StringRedisTemplate, Redis Cluster), Spring JDBC(JdbcTemplate), JPA/Hibernate(엔티티=스키마 정의용), JUnit5 + Mockito + H2(MODE=MySQL).

**대상 레포 2개:**
- `pocat` (메인앱) — `/Users/choejaemin/Desktop/POCAT/pocat` — Task 1
- `pocat-batch` (배치) — `/Users/choejaemin/Desktop/POCAT/pocat-batch` — Task 2~7

**배포 순서(중요):** Task 1(메인앱) 먼저 배포 → 구 키가 정적이 된 뒤 Task 2~6(배치) 배포 → 배치가 구 키 drain.

---

## File Structure

**pocat (메인앱):**
- Modify: `src/main/java/com/rocketcrew/pocat/domain/community/freepost/service/FreePostViewCountService.java` — BUFFER_KEY 해시태그
- Modify: `src/main/java/com/rocketcrew/pocat/domain/community/freepost/service/FreePostCommentCountService.java` — BUFFER_KEY 해시태그
- Modify: `src/main/java/com/rocketcrew/pocat/domain/community/tradepost/service/ViewCountService.java` — BUFFER_KEY 해시태그

**pocat-batch (배치):**
- Create: `src/main/java/com/rocketcrew/pocatbatch/domain/tradepost/entity/TradePost.java` — H2 스키마 생성 + prod validate 파리티용 최소 엔티티
- Create: `src/main/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepository.java` — JDBC batch 반영
- Create: `src/test/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepositoryTest.java`
- Modify: `src/main/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTasklet.java` — 거래글 + bulk + drain
- Create: `src/test/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTaskletTest.java`
- Delete: `src/main/java/com/rocketcrew/pocatbatch/domain/freepost/service/FreePostFlushService.java`
- Modify: `src/main/java/com/rocketcrew/pocatbatch/domain/freepost/repository/FreePostRepository.java` — per-row `@Modifying` 메서드 제거(랭킹 쿼리 유지)
- Modify(검토): `docs/RUNBOOK.md` / `docs/ARCHITECTURE.md` — Task 7

---

## Task 1: 메인앱 Redis 키 해시태그 표준화 (pocat 레포)

**Files:**
- Modify: `pocat/src/main/java/com/rocketcrew/pocat/domain/community/freepost/service/FreePostViewCountService.java:20`
- Modify: `pocat/src/main/java/com/rocketcrew/pocat/domain/community/freepost/service/FreePostCommentCountService.java:15`
- Modify: `pocat/src/main/java/com/rocketcrew/pocat/domain/community/tradepost/service/ViewCountService.java:20`

> 상수만 바꾸는 변경이라 TDD 대신 변경→검증→컴파일 순으로 진행한다. 모든 명령은 `pocat` 디렉터리에서 실행.

- [ ] **Step 1: 자유글 조회수 BUFFER_KEY 변경**

`FreePostViewCountService.java`에서:
```java
    static final String BUFFER_KEY = "view:free:buffer";
```
를 다음으로 변경:
```java
    static final String BUFFER_KEY = "{view:free}:buffer";
```

- [ ] **Step 2: 자유글 댓글수 BUFFER_KEY 변경**

`FreePostCommentCountService.java`에서:
```java
    static final String BUFFER_KEY = "comment:free:buffer";
```
를 다음으로 변경:
```java
    static final String BUFFER_KEY = "{comment:free}:buffer";
```

- [ ] **Step 3: 거래글 조회수 BUFFER_KEY 변경**

`ViewCountService.java`에서:
```java
    private static final String BUFFER_KEY = "view:buffer";
```
를 다음으로 변경:
```java
    private static final String BUFFER_KEY = "{view:trade}:buffer";
```

- [ ] **Step 4: 키 문자열을 단언하는 테스트가 있는지 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat && grep -rn "view:free:buffer\|comment:free:buffer\|\"view:buffer\"" src/test`
Expected: 결과 없음. (있으면 해당 테스트의 기대값도 해시태그 버전으로 수정)

- [ ] **Step 5: 컴파일 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
cd /Users/choejaemin/Desktop/POCAT/pocat
git add src/main/java/com/rocketcrew/pocat/domain/community/freepost/service/FreePostViewCountService.java \
        src/main/java/com/rocketcrew/pocat/domain/community/freepost/service/FreePostCommentCountService.java \
        src/main/java/com/rocketcrew/pocat/domain/community/tradepost/service/ViewCountService.java
git commit -m "fix: 조회수/댓글수 버퍼 키를 클러스터 해시태그로 표준화"
```

---

## Task 2: 배치 TradePost 엔티티 추가 (스키마/validate 파리티)

> 배치는 거래글 테이블에 raw JDBC로 쓰지만, 테스트 H2(`ddl-auto: create-drop`)에 `trade_posts` 테이블이 생성되어야 하고 prod(`validate`) 파리티도 필요하므로 최소 엔티티를 추가한다. 컬럼은 raw JDBC가 건드리는 `view_count`와 `BaseEntity` 공통 컬럼만 매핑한다.

**Files:**
- Create: `src/main/java/com/rocketcrew/pocatbatch/domain/tradepost/entity/TradePost.java`

- [ ] **Step 1: TradePost 엔티티 작성**

`src/main/java/com/rocketcrew/pocatbatch/domain/tradepost/entity/TradePost.java`:
```java
package com.rocketcrew.pocatbatch.domain.tradepost.entity;

import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "trade_posts")
@SQLRestriction("deleted_at IS NULL")
public class TradePost extends BaseEntity {

    @Column(name = "view_count", nullable = false)
    private int viewCount;
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
cd /Users/choejaemin/Desktop/POCAT/pocat-batch
git add src/main/java/com/rocketcrew/pocatbatch/domain/tradepost/entity/TradePost.java
git commit -m "feat: 배치 TradePost 최소 엔티티 추가 (스키마/validate 파리티)"
```

---

## Task 3: ViewCountBulkRepository (JDBC batch)

**Files:**
- Create: `src/main/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepository.java`
- Test: `src/test/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepositoryTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepositoryTest.java`:
```java
package com.rocketcrew.pocatbatch.domain.viewcount.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ViewCountBulkRepositoryTest {

    @Autowired
    private ViewCountBulkRepository bulkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void insertFreePost(long id, int viewCount, int commentCount) {
        jdbcTemplate.update(
                "INSERT INTO free_posts (id, user_id, title, content, view_count, comment_count) " +
                        "VALUES (?, 1, 't', 'c', ?, ?)",
                id, viewCount, commentCount);
    }

    private void insertTradePost(long id, int viewCount) {
        // 배치 TradePost 엔티티는 view_count만 매핑 → H2 trade_posts 테이블에는
        // id/view_count + BaseEntity 공통 컬럼만 존재한다. (price 등 미매핑 컬럼 INSERT 금지)
        jdbcTemplate.update(
                "INSERT INTO trade_posts (id, view_count) VALUES (?, ?)",
                id, viewCount);
    }

    private int freeViewCount(long id) {
        return jdbcTemplate.queryForObject("SELECT view_count FROM free_posts WHERE id = ?", Integer.class, id);
    }

    private int freeCommentCount(long id) {
        return jdbcTemplate.queryForObject("SELECT comment_count FROM free_posts WHERE id = ?", Integer.class, id);
    }

    private int tradeViewCount(long id) {
        return jdbcTemplate.queryForObject("SELECT view_count FROM trade_posts WHERE id = ?", Integer.class, id);
    }

    @Test
    void 자유글_조회수_다건_배치_증가() {
        insertFreePost(9001, 5, 0);
        insertFreePost(9002, 10, 0);

        bulkRepository.increaseFreePostViewCount(Map.of(9001L, 3, 9002L, 7));

        assertThat(freeViewCount(9001)).isEqualTo(8);
        assertThat(freeViewCount(9002)).isEqualTo(17);
    }

    @Test
    void 자유글_댓글수는_0_미만으로_내려가지_않음() {
        insertFreePost(9101, 0, 2);

        bulkRepository.increaseFreePostCommentCount(Map.of(9101L, -5));

        assertThat(freeCommentCount(9101)).isZero();
    }

    @Test
    void 거래글_조회수_배치_증가() {
        insertTradePost(9201, 4);

        bulkRepository.increaseTradePostViewCount(Map.of(9201L, 6));

        assertThat(tradeViewCount(9201)).isEqualTo(10);
    }

    @Test
    void 빈_맵은_아무것도_하지_않음() {
        int[] result = bulkRepository.increaseFreePostViewCount(Map.of());
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && ./gradlew test --tests "com.rocketcrew.pocatbatch.domain.viewcount.repository.ViewCountBulkRepositoryTest"`
Expected: 컴파일 실패 (`ViewCountBulkRepository` 클래스 없음)

- [ ] **Step 3: ViewCountBulkRepository 구현**

`src/main/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepository.java`:
```java
package com.rocketcrew.pocatbatch.domain.viewcount.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ViewCountBulkRepository {

    private static final String FREE_VIEW_SQL =
            "UPDATE free_posts SET view_count = view_count + ? WHERE id = ?";
    private static final String FREE_COMMENT_SQL =
            "UPDATE free_posts SET comment_count = GREATEST(0, comment_count + ?) WHERE id = ?";
    private static final String TRADE_VIEW_SQL =
            "UPDATE trade_posts SET view_count = view_count + ? WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] increaseFreePostViewCount(Map<Long, Integer> deltas) {
        return batchUpdate(FREE_VIEW_SQL, deltas);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] increaseFreePostCommentCount(Map<Long, Integer> deltas) {
        return batchUpdate(FREE_COMMENT_SQL, deltas);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] increaseTradePostViewCount(Map<Long, Integer> deltas) {
        return batchUpdate(TRADE_VIEW_SQL, deltas);
    }

    private int[] batchUpdate(String sql, Map<Long, Integer> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return new int[0];
        }
        List<Map.Entry<Long, Integer>> entries = new ArrayList<>(deltas.entrySet());
        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map.Entry<Long, Integer> entry = entries.get(i);
                ps.setInt(1, entry.getValue());
                ps.setLong(2, entry.getKey());
            }

            @Override
            public int getBatchSize() {
                return entries.size();
            }
        });
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && ./gradlew test --tests "com.rocketcrew.pocatbatch.domain.viewcount.repository.ViewCountBulkRepositoryTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
cd /Users/choejaemin/Desktop/POCAT/pocat-batch
git add src/main/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepository.java \
        src/test/java/com/rocketcrew/pocatbatch/domain/viewcount/repository/ViewCountBulkRepositoryTest.java
git commit -m "feat: 조회수/댓글수 JDBC batch 반영 ViewCountBulkRepository 추가"
```

---

## Task 4: Tasklet bulk 전환 + 거래글 추가

> 검증된 골격(`:failed` 재병합 → 이전 `processing` 복구 → `RENAME` → `flushKey`)은 유지하고, `flushKey`의 per-entry 루프를 `Map` 수집 후 bulk 1회 호출로 바꾼다. 거래글 버퍼(`TRADE_VIEW`)를 추가한다. `FreePostFlushService` 의존을 제거하고 `ViewCountBulkRepository`로 교체한다.
> 테스트 용이성을 위해 순수 수집 로직 `collectDeltas`(static, package-private)와 `flushKey`(package-private)를 직접 단위 테스트한다.

**Files:**
- Modify: `src/main/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTasklet.java`
- Test: `src/test/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTaskletTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTaskletTest.java`:
```java
package com.rocketcrew.pocatbatch.job.viewcount;

import com.rocketcrew.pocatbatch.domain.viewcount.repository.ViewCountBulkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        return new org.springframework.data.redis.core.DefaultTypedTuple<>(value, score);
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
        verify(zSetOps, never()).incrementScore(anyString(), anyString(), anyLong());
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
        verify(zSetOps, never()).incrementScore(anyString(), anyString(), anyLong());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && ./gradlew test --tests "com.rocketcrew.pocatbatch.job.viewcount.ViewCountFlushTaskletTest"`
Expected: 컴파일 실패 (`collectDeltas`/`flushKey`/`drainLegacyKey`/`FlushType.TRADE_VIEW`/`bulkRepository` 없음)

- [ ] **Step 3: ViewCountFlushTasklet 전면 교체**

`src/main/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTasklet.java` 전체를 다음으로 교체:
```java
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && ./gradlew test --tests "com.rocketcrew.pocatbatch.job.viewcount.ViewCountFlushTaskletTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
cd /Users/choejaemin/Desktop/POCAT/pocat-batch
git add src/main/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTasklet.java \
        src/test/java/com/rocketcrew/pocatbatch/job/viewcount/ViewCountFlushTaskletTest.java
git commit -m "feat: 조회수 flush 거래글 추가 + bulk 전환 + 구 키 drain"
```

---

## Task 5: 사용하지 않는 per-row 코드 제거

> Task 4에서 `ViewCountFlushTasklet`이 `FreePostFlushService`를 더 이상 쓰지 않는다. per-row 반영 코드를 제거해 dead code를 없앤다. 랭킹용 `findTopByPopularScore`는 유지한다.

**Files:**
- Delete: `src/main/java/com/rocketcrew/pocatbatch/domain/freepost/service/FreePostFlushService.java`
- Modify: `src/main/java/com/rocketcrew/pocatbatch/domain/freepost/repository/FreePostRepository.java`

- [ ] **Step 1: 잔여 참조 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && grep -rn "FreePostFlushService" src`
Expected: 결과 없음 (Task 4에서 의존 제거됨). 결과가 있으면 먼저 그 참조를 정리.

- [ ] **Step 2: FreePostFlushService 삭제**

```bash
cd /Users/choejaemin/Desktop/POCAT/pocat-batch
git rm src/main/java/com/rocketcrew/pocatbatch/domain/freepost/service/FreePostFlushService.java
```

- [ ] **Step 3: FreePostRepository에서 per-row 메서드 제거**

`src/main/java/com/rocketcrew/pocatbatch/domain/freepost/repository/FreePostRepository.java` 전체를 다음으로 교체:
```java
package com.rocketcrew.pocatbatch.domain.freepost.repository;

import com.rocketcrew.pocatbatch.domain.freepost.entity.FreePost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FreePostRepository extends JpaRepository<FreePost, Long> {

    int COMMENT_WEIGHT = 3;

    @Query("SELECT f FROM FreePost f WHERE f.createdAt >= :since ORDER BY (f.viewCount + f.commentCount * 3) DESC")
    List<FreePost> findTopByPopularScore(Pageable pageable, @Param("since") LocalDateTime since);
}
```

- [ ] **Step 4: 전체 테스트 통과 확인**

Run: `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && ./gradlew test`
Expected: BUILD SUCCESSFUL (기존 `ViewCountFlushJobConfigTest` 포함 전부 PASS)

- [ ] **Step 5: 커밋**

```bash
cd /Users/choejaemin/Desktop/POCAT/pocat-batch
git add -A
git commit -m "refactor: 사용하지 않는 per-row 조회수 반영 코드 제거"
```

---

## Task 6: JDBC URL rewriteBatchedStatements 확인

> batchUpdate의 네트워크 라운드트립 절감 효과는 MySQL 드라이버 옵션 `rewriteBatchedStatements=true`에서 나온다. 배치 datasource URL은 AWS Parameter Store(`${DB_URL}`)에 있어 코드 레포에서 직접 못 바꾼다. 확인/적용만 한다.

**Files:** (코드 변경 없음 — 인프라 설정)

- [ ] **Step 1: 현재 DB_URL에 옵션 포함 여부 확인**

운영 담당자/Parameter Store에서 `/pocat/prod/` 의 `DB_URL` 값을 확인.
Expected: `jdbc:mysql://...?...&rewriteBatchedStatements=true` 형태. 없으면 추가.

- [ ] **Step 2: 미적용 시 추가 요청**

`DB_URL` 쿼리스트링에 `rewriteBatchedStatements=true` 추가 (이미 다른 파라미터가 있으면 `&`로 연결). 배치 인스턴스 재기동 시 반영.

> 기능 정확성은 옵션 없이도 동일하다(단지 라운드트립이 덜 묶일 뿐). 따라서 이 Task는 배포 차단 요소가 아니라 성능 최적화 확인 항목이다.

---

## Task 7: 문서 갱신

**Files:**
- Modify: `docs/RUNBOOK.md`
- Modify: `docs/ARCHITECTURE.md`

- [ ] **Step 1: RUNBOOK에 거래글 flush + 키 컨벤션 반영**

`docs/RUNBOOK.md`의 `viewCountFlushJob` 관련 항목에 다음을 반영(해당 섹션이 없으면 조회수 배치 설명 위치에 추가):
- 처리 버퍼: `{view:free}:buffer`, `{comment:free}:buffer`, `{view:trade}:buffer` (1분 주기)
- 반영 방식: JDBC batch(한 번에 처리), 실패분 `:failed` ZSet 24h 재시도
- 키 표준화 배포 후 구 키(`view:free:buffer`, `comment:free:buffer`, `view:buffer`) 1회 drain됨 → drain 완료 후 후속 PR에서 drain 코드 제거 예정

- [ ] **Step 2: ARCHITECTURE에 거래글 조회수 흐름 추가**

`docs/ARCHITECTURE.md`의 조회수/커뮤니티 데이터 흐름 설명에 거래글 조회수가 `{view:trade}:buffer`를 통해 동일 패턴으로 반영됨을 1~2줄 추가.

- [ ] **Step 3: 커밋**

```bash
cd /Users/choejaemin/Desktop/POCAT/pocat-batch
git add docs/RUNBOOK.md docs/ARCHITECTURE.md
git commit -m "docs: 거래글 조회수 flush + 키 표준화 RUNBOOK/ARCHITECTURE 반영"
```

---

## 최종 검증 (전체 완료 후)

- [ ] `cd /Users/choejaemin/Desktop/POCAT/pocat-batch && ./gradlew test` → 전부 PASS
- [ ] `cd /Users/choejaemin/Desktop/POCAT/pocat && ./gradlew compileJava` → SUCCESS
- [ ] 배포 순서 준수: 메인앱(Task 1) → 배치(Task 2~6)
- [ ] 배포 후 로그에서 `legacy 버퍼 drain 완료` 1회 확인 → 후속 PR로 drain 코드 제거 예약
