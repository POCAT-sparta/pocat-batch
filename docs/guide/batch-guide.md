# pocat-batch 개발 가이드

> 대상: pocat-batch에 자신의 담당 도메인 스케줄링 Job을 추가하려는 팀원
>
> 이 문서는 **배치 서버가 어떻게 동작하는지 이해하고**,
> **순서대로 따라만 해도 새 Job을 추가할 수 있도록** 작성되었습니다.

---

## 목차

1. [왜 배치 서버가 필요한가](#1-왜-배치-서버가-필요한가)
2. [전체 동작 흐름 이해](#2-전체-동작-흐름-이해)
3. [Spring Batch 핵심 개념 (5분 요약)](#3-spring-batch-핵심-개념-5분-요약)
4. [프로젝트 구조](#4-프로젝트-구조)
5. [새 Job 추가 — 단계별 가이드](#5-새-job-추가--단계별-가이드)
   - [Step 0. 무엇을 배치로 옮길지 판단하기](#step-0-무엇을-배치로-옮길지-판단하기)
   - [Step 1. 패키지 디렉터리 생성](#step-1-패키지-디렉터리-생성)
   - [Step 2. 엔티티 복제](#step-2-엔티티-복제)
   - [Step 3. Repository 작성](#step-3-repository-작성)
   - [Step 4. Service 작성 (DB 쓰기 Job인 경우)](#step-4-service-작성-db-쓰기-job인-경우)
   - [Step 5. Tasklet 작성](#step-5-tasklet-작성)
   - [Step 6. JobConfig 작성](#step-6-jobconfig-작성)
   - [Step 7. BatchScheduler에 등록](#step-7-batchscheduler에-등록)
   - [Step 8. application.yaml 설정 추가](#step-8-applicationyaml-설정-추가)
   - [Step 9. 테스트 작성](#step-9-테스트-작성)
   - [Step 10. 로컬 실행 및 검증](#step-10-로컬-실행-및-검증)
6. [자주 하는 실수와 해결법](#6-자주-하는-실수와-해결법)
7. [참고: 자유게시판 샘플 전체 구조](#7-참고-자유게시판-샘플-전체-구조)

---

## 1. 왜 배치 서버가 필요한가

메인 앱(`pocat`)은 `@Scheduled` 어노테이션으로 스케줄링 작업을 실행합니다.
단일 서버에서는 문제가 없지만, **앱 서버를 2대 이상 띄우는 순간** 모든 인스턴스가 동시에 스케줄러를 실행합니다.

```
[문제 상황]
App Server 1 ──▶ @Scheduled 실행 ──▶ Redis RENAME ──▶ MySQL 쓰기
App Server 2 ──▶ @Scheduled 실행 ──▶ Redis RENAME ──▶ MySQL 중복 쓰기 ← 데이터 정합성 파괴
```

`pocat-batch`는 **스케줄링을 담당하는 별도 서버 1대**를 두어 이 문제를 해결합니다.

```
App Server 1 ──▶ 일반 요청 처리만
App Server 2 ──▶ 일반 요청 처리만
Batch Server ──▶ 스케줄링 작업 독점 실행
```

---

## 2. 전체 동작 흐름 이해

배치 서버가 시작되면 다음 순서로 동작합니다.

```
[서버 시작]
  └─ PocatBatchApplication.main()
       ├─ .env 파일 로드 (OS 환경변수 우선)
       └─ SpringApplication 시작

[주기적 실행 — fixedDelay=60초]
  BatchScheduler
    ├─ runFreePostRanking()
    │    └─ JobLauncher.run(freePostRankingJob, params)   ← Job 실행 요청
    │         └─ freePostRankingStep
    │              └─ FreePostRankingTasklet.execute()    ← 실제 비즈니스 로직
    │                   ├─ DB에서 인기 게시글 조회
    │                   └─ Redis ZSet 갱신 (RENAME 패턴)
    │
    └─ runViewCountFlush()
         └─ JobLauncher.run(viewCountFlushJob, params)
              └─ viewCountFlushStep
                   └─ ViewCountFlushTasklet.execute()
                        ├─ Redis 버퍼 → MySQL 조회수 반영
                        └─ Redis 버퍼 → MySQL 댓글수 반영
```

### fixedDelay vs fixedRate

- `fixedDelay=60_000` — **이전 실행이 완료된 후** 60초 대기 → 실행
- `fixedRate=60_000` — 시작 시각 기준 60초마다 실행 (이전 실행이 오래 걸리면 중첩 가능)
- **우리는 fixedDelay 사용** — 배치 작업이 60초 이상 걸려도 중첩 실행되지 않음

### JobParameters와 ts

```java
JobParameters params = new JobParametersBuilder()
        .addLong("ts", System.currentTimeMillis())
        .toJobParameters();
```

Spring Batch는 **동일 JobParameters로 같은 Job을 두 번 실행하지 않습니다.**
`ts`(타임스탬프)를 파라미터로 넣어 매번 새로운 파라미터로 실행되도록 합니다.

### BATCH_* 메타테이블

MySQL에 자동 생성되는 테이블로 모든 Job 실행 기록을 저장합니다.

| 테이블 | 저장 내용 |
|--------|----------|
| `BATCH_JOB_INSTANCE` | Job 이름 + 파라미터 조합 |
| `BATCH_JOB_EXECUTION` | 실행 시작·종료 시각, 성공/실패 상태 |
| `BATCH_STEP_EXECUTION` | Step별 처리 건수, 시간 |

이 덕분에 배포 환경에서 **언제 어떤 Job이 얼마나 걸렸는지** 쿼리로 추적할 수 있습니다.

---

## 3. Spring Batch 핵심 개념 (5분 요약)

### Job → Step → Tasklet 계층 구조

```
Job (전체 배치 작업 단위)
 └─ Step (Job을 구성하는 단계)
      └─ Tasklet (Step 안에서 실행되는 실제 로직)
```

우리 프로젝트는 **Job 1개 = Step 1개 = Tasklet 1개** 구조입니다.
복잡한 처리가 필요하면 Step을 여러 개 추가할 수 있지만, 지금은 단순하게 유지합니다.

### Tasklet 인터페이스

```java
public interface Tasklet {
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext);
}
```

- `execute()` 안에 실제 비즈니스 로직을 작성합니다.
- 반환값은 항상 `RepeatStatus.FINISHED` — "이 Step은 한 번 실행하고 끝"이라는 의미입니다.

### JobConfig 역할

```java
@Configuration
public class MyJobConfig {

    @Bean(name = "myJob")
    public Job myJob() { ... }          // Job 빈 등록

    @Bean(name = "myStep")
    public Step myStep() { ... }        // Step 빈 등록 (Tasklet 연결)
}
```

JobConfig는 Job과 Step 빈을 Spring 컨텍스트에 등록하는 설정 클래스입니다.
실제 로직은 Tasklet에 있고, JobConfig는 "어떤 Tasklet을 어떤 순서로 실행하는 Job인지"를 정의합니다.

---

## 4. 프로젝트 구조

```
com.rocketcrew.pocatbatch/
├── PocatBatchApplication          ← 진입점. dotenv 로드 + Spring 시작
├── config/
│   ├── BatchConfig                ← Spring Batch 자동구성 커스터마이징 (현재 비어있음)
│   └── JpaConfig                  ← JPA Auditing 활성화
├── domain/
│   └── {도메인명}/                ← 도메인별 패키지 (freepost, trade, auction 등)
│       ├── entity/                ← 메인 앱에서 복제한 엔티티 (읽기에 필요한 필드만)
│       ├── repository/            ← JPA Repository
│       └── service/               ← DB 쓰기 시 REQUIRES_NEW 트랜잭션 처리
├── job/
│   └── {jobname}/                 ← Job별 패키지
│       ├── {Name}JobConfig        ← Job + Step 빈 등록
│       └── {Name}Tasklet          ← 실제 배치 로직
└── scheduler/
    └── BatchScheduler             ← @Scheduled 트리거 + JobLauncher 호출
```

**새 도메인 Job 추가 시 건드리는 파일:**

| 파일 | 역할 |
|------|------|
| `domain/{도메인}/entity/*.java` | 엔티티 복제 (신규 생성) |
| `domain/{도메인}/repository/*.java` | Repository (신규 생성) |
| `domain/{도메인}/service/*.java` | DB 쓰기 서비스 (필요 시 신규 생성) |
| `job/{jobname}/{Name}Tasklet.java` | Tasklet 로직 (신규 생성) |
| `job/{jobname}/{Name}JobConfig.java` | Job/Step 빈 등록 (신규 생성) |
| `scheduler/BatchScheduler.java` | @Scheduled 메서드 추가 + 생성자 수정 |
| `src/main/resources/application.yaml` | 설정값 추가 (필요 시) |

---

## 5. 새 Job 추가 — 단계별 가이드

아래 예시는 **"경매(Auction) 낙찰 집계"** Job을 가정합니다.
실제 작업에 맞게 클래스명·패키지명·비즈니스 로직만 바꾸면 됩니다.

---

### Step 0. 무엇을 배치로 옮길지 판단하기

메인 앱의 `@Scheduled` 스케줄러 클래스를 찾아 확인합니다.

```
pocat/
└── src/main/java/...
    └── scheduler/
        └── AuctionSettlementScheduler.java   ← 이것을 배치로 이전
```

해당 스케줄러가 어떤 작업을 하는지 파악합니다.

- **Redis 버퍼 → MySQL 플러시 패턴?** → `ViewCountFlushTasklet` 참고
- **DB 조회 → Redis 캐시 갱신 패턴?** → `FreePostRankingTasklet` 참고
- **단순 DB 집계/업데이트?** → Tasklet에서 직접 Repository 호출

---

### Step 1. 패키지 디렉터리 생성

아래 구조로 빈 패키지를 만듭니다. (IDE에서 패키지 생성 또는 디렉터리 직접 생성)

```
src/main/java/com/rocketcrew/pocatbatch/
├── domain/
│   └── auction/                  ← 신규
│       ├── entity/
│       ├── repository/
│       └── service/              (DB 쓰기 필요 시)
└── job/
    └── auction/                  ← 신규 (job 이름 기준으로 명명)
```

---

### Step 2. 엔티티 복제

> **원칙:** 메인 앱의 엔티티를 공유 라이브러리 없이 **복제**합니다.
> 배치에서 필요한 컬럼만 포함하면 됩니다.

#### 2-1. BaseEntity 확인

`domain/freepost/entity/BaseEntity.java`를 참고해 동일하게 작성합니다.
(이미 존재하면 그대로 사용하거나 공통 패키지로 이동해도 됩니다.)

```java
// domain/auction/entity/BaseEntity.java
// (freepost/BaseEntity와 동일하면 공통 패키지로 이동해도 됨)
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
```

#### 2-2. 도메인 엔티티 복제

메인 앱의 엔티티에서 **배치에 필요한 필드만** 복사합니다.

```java
// domain/auction/entity/Auction.java
package com.rocketcrew.pocatbatch.domain.auction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auctions")                 // ← 메인 앱 DB 테이블명과 반드시 일치
@SQLRestriction("deleted_at IS NULL")     // ← 소프트 삭제 사용 시 반드시 추가
public class Auction extends BaseEntity {

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AuctionStatus status;

    @Column(name = "final_price")
    private Long finalPrice;

    // 배치에서 필요한 필드만 복사. 모든 필드를 복사할 필요 없음.
}
```

> **주의사항:**
> - `@Table(name = "...")` — 메인 앱의 실제 테이블명과 **정확히** 일치해야 합니다.
> - `@SQLRestriction("deleted_at IS NULL")` — 메인 앱 엔티티에 소프트 삭제가 있으면 반드시 추가합니다. 없으면 배치에서 삭제된 데이터를 조회하게 됩니다.
> - `@Column(name = "...")` — 컬럼명도 메인 앱과 일치해야 합니다.
> - 관계 매핑(`@OneToMany`, `@ManyToOne` 등)은 배치에서 필요하지 않으면 넣지 않습니다.

---

### Step 3. Repository 작성

```java
// domain/auction/repository/AuctionRepository.java
package com.rocketcrew.pocatbatch.domain.auction.repository;

import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.entity.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    // 예시: 마감된 경매 목록 조회
    List<Auction> findByStatusAndUpdatedAtBefore(AuctionStatus status, LocalDateTime before);

    // DB 업데이트를 하는 경우 반드시 int 반환 (영향 행 수 검증용)
    @Modifying
    @Query("UPDATE Auction a SET a.status = 'CLOSED' WHERE a.id = :id")
    int closeAuction(@Param("id") Long id);
}
```

> **규칙:** DB를 수정하는 `@Modifying` 쿼리는 **반드시 `int` 반환**합니다.
> `void`로 하면 영향 행이 0인지(삭제된 데이터인지) 확인할 수 없습니다.

---

### Step 4. Service 작성 (DB 쓰기 Job인 경우)

> **이 Step은 DB를 쓰는 Job에만 필요합니다.**
> Redis 캐시만 갱신하는 Job(랭킹 갱신 등)은 Tasklet에서 직접 Repository를 호출해도 됩니다.

DB 쓰기 Service를 **별도 빈**으로 분리하는 이유:
- Tasklet이 자기 자신의 메서드를 호출하면 `@Transactional`이 동작하지 않습니다. (Spring AOP 프록시 우회)
- 별도 빈에서 호출해야 트랜잭션이 제대로 적용됩니다.
- `REQUIRES_NEW`로 레코드별 독립 트랜잭션을 보장합니다. (한 건 실패가 전체 롤백으로 번지지 않음)

```java
// domain/auction/service/AuctionBatchService.java
package com.rocketcrew.pocatbatch.domain.auction.service;

import com.rocketcrew.pocatbatch.domain.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBatchService {

    private final AuctionRepository auctionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(Long auctionId) {
        int updated = auctionRepository.closeAuction(auctionId);
        if (updated == 0) {
            // 0행이면 삭제된 데이터 — 재시도해도 0행이므로 경고 로그 후 종료
            log.warn("closeAuction skipped: auctionId={} not found (deleted), no retry", auctionId);
        }
    }
}
```

> **핵심 규칙:**
> - `@Transactional(propagation = Propagation.REQUIRES_NEW)` — 레코드 하나씩 독립 커밋
> - DB 업데이트 결과가 0행이면 `log.warn` 후 그냥 리턴 (예외 던지면 안 됨)
> - 예외를 던지면 → Tasklet이 catch → `failedKey` 재적재 → 다음 사이클 재시도 → 또 0행 → 무한 루프
> - 삭제된 데이터에 대한 0행은 **비재시도 종단 경로(terminal path)**입니다.

---

### Step 5. Tasklet 작성

Tasklet은 실제 비즈니스 로직이 담기는 핵심 클래스입니다.

#### 패턴 A: Redis 캐시 갱신 (랭킹 갱신 패턴)

`FreePostRankingTasklet`을 참고합니다.

```java
// job/auction/AuctionRankingTasklet.java
package com.rocketcrew.pocatbatch.job.auction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRankingTasklet implements Tasklet {

    // Redis 키는 상수로 관리. 메인 앱과 동일한 키 이름 사용.
    public static final String RANKING_KEY     = "ranking:auction:popular";
    private static final String RANKING_NEW_KEY = "ranking:auction:popular:new";

    private final StringRedisTemplate redisTemplate;
    private final AuctionRepository auctionRepository;  // 도메인에 맞게 변경

    @Value("${pocat.batch.ranking.auction.cache-size:50}")
    private int cacheSize;

    @Value("${pocat.batch.ranking.auction.ttl-seconds:70}")
    private int ttlSeconds;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<?> items = auctionRepository.findTopRanked(cacheSize);  // 조회 로직 구현

        if (items.isEmpty()) {
            log.info("경매 랭킹 갱신 대상 없음");
            return RepeatStatus.FINISHED;
        }

        try {
            redisTemplate.delete(RANKING_NEW_KEY);

            for (/* item : items */) {
                double score = /* 점수 계산 */;
                redisTemplate.opsForZSet().add(RANKING_NEW_KEY, item.getId().toString(), score);
            }

            // hasKey 체크 후 rename — key 없이 rename하면 예외 발생
            if (Boolean.TRUE.equals(redisTemplate.hasKey(RANKING_NEW_KEY))) {
                redisTemplate.rename(RANKING_NEW_KEY, RANKING_KEY);
                redisTemplate.expire(RANKING_KEY, ttlSeconds, TimeUnit.SECONDS);
                log.info("경매 랭킹 갱신 완료: {}개", items.size());
            }
        } finally {
            // rename 실패나 예외가 발생해도 임시 키는 반드시 삭제
            redisTemplate.delete(RANKING_NEW_KEY);
        }

        return RepeatStatus.FINISHED;
    }
}
```

#### 패턴 B: Redis 버퍼 → MySQL 플러시

`ViewCountFlushTasklet`을 참고합니다.

```java
// job/auction/AuctionFlushTasklet.java
package com.rocketcrew.pocatbatch.job.auction;

import com.rocketcrew.pocatbatch.domain.auction.service.AuctionBatchService;
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
public class AuctionFlushTasklet implements Tasklet {

    // 메인 앱에서 사용하는 Redis 키와 동일해야 합니다
    private static final String BUFFER_KEY     = "auction:bid:buffer";
    private static final String PROCESSING_KEY = "auction:bid:buffer:processing";

    private final StringRedisTemplate redisTemplate;
    private final AuctionBatchService auctionBatchService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 독립적인 작업이 여러 개라면 runSafely로 감싸 하나 실패가 전체를 막지 않도록
        runSafely("flushAuctionBid", () -> flushBuffer(PROCESSING_KEY, BUFFER_KEY));
        return RepeatStatus.FINISHED;
    }

    private void runSafely(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("{} 실패", label, e);
        }
    }

    private void flushBuffer(String processingKey, String bufferKey) {
        String failedKey = processingKey + ":failed";

        // 이전 실패 항목 재병합
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

        // 이전 주기 미처리 키 재처리
        if (Boolean.TRUE.equals(redisTemplate.hasKey(processingKey))) {
            log.warn("미처리 데이터 발견, DB 업데이트 재시도: key={}", processingKey);
            flushKey(processingKey);
        }

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(bufferKey))) {
            return;
        }

        redisTemplate.rename(bufferKey, processingKey);
        flushKey(processingKey);
    }

    private void flushKey(String key) {
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
                Long auctionId = Long.parseLong(entry.getValue());
                int delta = (int) Math.round(entry.getScore());
                auctionBatchService.closeAuction(auctionId);  // 도메인 로직 호출
            } catch (Exception e) {
                log.error("flush 실패: entry={}, key={}", entry.getValue(), key, e);
                // 실패 항목은 failedKey에 적재 → 다음 주기에 재처리
                redisTemplate.opsForZSet().incrementScore(failedKey, entry.getValue(), entry.getScore());
                redisTemplate.expire(failedKey, 24, TimeUnit.HOURS);
            }
        }

        redisTemplate.delete(key);
    }
}
```

#### 패턴 C: 단순 DB 집계/업데이트 (Redis 없음)

```java
// job/auction/AuctionSettlementTasklet.java
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSettlementTasklet implements Tasklet {

    private final AuctionRepository auctionRepository;
    private final AuctionBatchService auctionBatchService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        List<Auction> expiredAuctions = auctionRepository
                .findByStatusAndUpdatedAtBefore(AuctionStatus.BIDDING, cutoff);

        log.info("정산 대상 경매: {}건", expiredAuctions.size());

        for (Auction auction : expiredAuctions) {
            try {
                auctionBatchService.closeAuction(auction.getId());
            } catch (Exception e) {
                log.error("경매 정산 실패: auctionId={}", auction.getId(), e);
                // 개별 실패를 로그하고 계속 진행 — 전체 Job 실패로 만들지 않음
            }
        }

        return RepeatStatus.FINISHED;
    }
}
```

---

### Step 6. JobConfig 작성

```java
// job/auction/AuctionSettlementJobConfig.java
package com.rocketcrew.pocatbatch.job.auction;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class AuctionSettlementJobConfig {

    // Job 이름은 전체 프로젝트에서 유일해야 합니다
    public static final String JOB_NAME  = "auctionSettlementJob";
    public static final String STEP_NAME = "auctionSettlementStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AuctionSettlementTasklet auctionSettlementTasklet;

    @Bean(name = JOB_NAME)
    public Job auctionSettlementJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(auctionSettlementStep())
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step auctionSettlementStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(auctionSettlementTasklet, transactionManager)
                .build();
    }
}
```

> **주의:** `JOB_NAME`은 프로젝트 전체에서 중복되면 안 됩니다.
> 기존 Job 이름 목록을 확인하고 새로운 이름을 사용하세요.
>
> 현재 사용 중인 Job 이름:
> - `freePostRankingJob`
> - `viewCountFlushJob`

---

### Step 7. BatchScheduler에 등록

`BatchScheduler.java`는 **반드시 수동 생성자**를 사용합니다.
`@RequiredArgsConstructor`는 `@Qualifier`를 지원하지 않기 때문입니다.

```java
// scheduler/BatchScheduler.java 수정
@Slf4j
@Component
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job freePostRankingJob;
    private final Job viewCountFlushJob;
    private final Job auctionSettlementJob;   // ← 추가

    // @RequiredArgsConstructor 대신 수동 생성자 사용 — @Qualifier 지원을 위해
    public BatchScheduler(
            JobLauncher jobLauncher,
            @Qualifier(FreePostRankingJobConfig.JOB_NAME)     Job freePostRankingJob,
            @Qualifier(ViewCountFlushJobConfig.JOB_NAME)      Job viewCountFlushJob,
            @Qualifier(AuctionSettlementJobConfig.JOB_NAME)   Job auctionSettlementJob) {  // ← 추가
        this.jobLauncher         = jobLauncher;
        this.freePostRankingJob  = freePostRankingJob;
        this.viewCountFlushJob   = viewCountFlushJob;
        this.auctionSettlementJob = auctionSettlementJob;  // ← 추가
    }

    @Scheduled(fixedDelay = 60_000)
    public void runFreePostRanking() {
        launch(freePostRankingJob, "freePostRankingJob");
    }

    @Scheduled(fixedDelay = 60_000)
    public void runViewCountFlush() {
        launch(viewCountFlushJob, "viewCountFlushJob");
    }

    // ↓ 새로 추가
    @Scheduled(fixedDelay = 300_000)   // 5분마다 — 도메인에 맞는 주기로 설정
    public void runAuctionSettlement() {
        launch(auctionSettlementJob, "auctionSettlementJob");
    }

    private void launch(Job job, String label) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("ts", System.currentTimeMillis())
                    .toJobParameters();
            JobExecution jobExecution = jobLauncher.run(job, params);
            if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                log.info("{} 실행 완료", label);
            } else {
                log.error("{} 비정상 종료: status={}, failures={}",
                        label, jobExecution.getStatus(), jobExecution.getAllFailureExceptions());
            }
        } catch (Exception e) {
            log.error("{} 실행 실패", label, e);
        }
    }
}
```

> **fixedDelay 값 결정 기준:**
> - 조회수/댓글수 플러시처럼 실시간성이 중요하면 `60_000` (1분)
> - 랭킹 갱신처럼 약간의 지연이 허용되면 `60_000` ~ `300_000` (1~5분)
> - 일일 정산처럼 자주 실행할 필요 없으면 `3_600_000` (1시간) 또는 Cron 표현식 사용

---

### Step 8. application.yaml 설정 추가

Tasklet에서 `@Value`로 읽을 설정값을 추가합니다.

```yaml
# src/main/resources/application.yaml

pocat:
  batch:
    ranking:
      free:
        cache-size: 100
        ttl-seconds: 70
        popular-days: 7
        comment-weight: 3
      auction:              # ← 추가 (필요한 경우)
        cache-size: 50
        ttl-seconds: 70
    settlement:
      auction:
        delay-minutes: 5    # 마감 판단 기준 시간
```

`.env.example`에도 새로 추가한 환경변수가 있다면 반영합니다.

---

### Step 9. 테스트 작성

기존 테스트(`FreePostRankingJobConfigTest`, `ViewCountFlushJobConfigTest`)를 참고합니다.

```java
// test/java/.../job/auction/AuctionSettlementJobConfigTest.java
@SpringBatchTest
@SpringBootTest
class AuctionSettlementJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    void auctionSettlementJob_정상실행() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
```

> **테스트 환경 주의사항:**
> - 현재 테스트는 실제 Redis 연결이 필요합니다.
> - 로컬에 Redis가 없으면 테스트가 실패합니다.
> - Redis 없이 테스트하려면 Testcontainers 또는 embedded-redis 도입이 필요합니다. (향후 개선 예정)

---

### Step 10. 로컬 실행 및 검증

#### 10-1. .env 파일 준비

```bash
cp .env.example .env
# .env 파일을 열어 실제 값 입력
```

```env
DB_URL=jdbc:mysql://localhost:3306/pocat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
BATCH_SERVER_PORT=8081
```

#### 10-2. 서버 실행

```bash
./gradlew bootRun
```

또는 IDE에서 `PocatBatchApplication` 실행.

#### 10-3. 실행 확인

서버 시작 로그에서 Job 등록 확인:

```
Started PocatBatchApplication in 3.2 seconds
```

60초(또는 설정한 주기) 후 Job 실행 로그 확인:

```
INFO  BatchScheduler - auctionSettlementJob 실행 완료
```

#### 10-4. DB 메타테이블로 실행 이력 확인

```sql
-- Job 실행 이력
SELECT ji.JOB_NAME, je.START_TIME, je.END_TIME, je.STATUS
FROM BATCH_JOB_INSTANCE ji
JOIN BATCH_JOB_EXECUTION je ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
ORDER BY je.START_TIME DESC
LIMIT 20;
```

#### 10-5. 헬스체크

```bash
curl http://localhost:8081/actuator/health
```

---

## 6. 자주 하는 실수와 해결법

### 실수 1: `@EnableBatchProcessing` 추가

```java
// ❌ 잘못된 코드
@EnableBatchProcessing  // Spring Boot 3.x에서는 추가하면 안 됨
@SpringBootApplication
public class PocatBatchApplication { ... }
```

**증상:** `JobRepository`, `JobLauncher` 빈 충돌 오류
**이유:** Spring Boot 3.x는 자동구성으로 Batch 빈을 제공. `@EnableBatchProcessing`을 추가하면 자동구성과 충돌.
**해결:** `@EnableBatchProcessing` 제거

---

### 실수 2: Job 이름 중복

```java
// ❌ 잘못된 코드 — 이미 존재하는 이름 사용
public static final String JOB_NAME = "freePostRankingJob";
```

**증상:** `No qualifying bean of type 'Job'` 또는 잘못된 Job이 실행됨
**해결:** 고유한 Job 이름 사용. `@Qualifier`로 명시적으로 지정.

---

### 실수 3: `@RequiredArgsConstructor`로 `@Qualifier` 사용

```java
// ❌ 잘못된 코드 — Lombok이 @Qualifier 처리 못함
@RequiredArgsConstructor
public class BatchScheduler {
    @Qualifier("freePostRankingJob")
    private final Job freePostRankingJob;  // Qualifier가 무시됨
```

**증상:** `expected single matching bean but found 2` — Job 빈이 여러 개라 주입 실패
**해결:** 수동 생성자 작성 + 생성자 파라미터에 `@Qualifier` 적용

---

### 실수 4: Tasklet에서 Service 자기 자신 호출

```java
// ❌ 잘못된 코드 — 트랜잭션 미적용
@Component
public class MyTasklet implements Tasklet {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void doWork() { ... }   // 자기 자신 호출 → AOP 프록시 우회 → 트랜잭션 없음
```

**해결:** DB 쓰기는 반드시 **별도 Service 빈**에 분리하고 해당 빈을 주입받아 호출.

---

### 실수 5: DB 업데이트 void 반환

```java
// ❌ 잘못된 코드
@Modifying
@Query("UPDATE FreePost f SET f.viewCount = f.viewCount + :count WHERE f.id = :postId")
void increaseViewCount(...);  // 0행인지 알 수 없음
```

**증상:** 삭제된 게시글에 대한 업데이트가 silently 실패 → 무한 재시도
**해결:** `int` 반환 → `updated == 0`이면 `log.warn` 후 리턴 (예외 던지지 않음)

---

### 실수 6: `rename()` 호출 전 key 존재 확인 생략

```java
// ❌ 잘못된 코드
redisTemplate.rename(RANKING_NEW_KEY, RANKING_KEY);  // key 없으면 예외
```

**증상:** `ERR no such key` Redis 예외
**해결:**

```java
if (Boolean.TRUE.equals(redisTemplate.hasKey(RANKING_NEW_KEY))) {
    redisTemplate.rename(RANKING_NEW_KEY, RANKING_KEY);
}
```

---

### 실수 7: `.env` 파일의 인라인 주석

```env
# ❌ dotenv-java는 인라인 주석을 지원하지 않음
DB_PASSWORD=mypassword   # 이 부분이 값으로 포함됨!
```

**증상:** DB 연결 실패 — 비밀번호에 `   # 이 부분이 값으로 포함됨!`이 붙음
**해결:** 주석은 반드시 **별도 줄**에 작성

```env
# 개발 환경 비밀번호
DB_PASSWORD=mypassword
```

---

## 7. 참고: 자유게시판 샘플 전체 구조

자유게시판 구현(`freepost`)이 이 프로젝트의 샘플 코드입니다.
새 도메인 구현 전에 아래 파일들을 순서대로 읽으면 패턴을 파악할 수 있습니다.

```
읽는 순서:
1. domain/freepost/entity/BaseEntity.java          → 공통 엔티티 필드
2. domain/freepost/entity/FreePost.java             → 엔티티 복제 방식, @SQLRestriction
3. domain/freepost/repository/FreePostRepository.java → @Modifying int 반환, 복잡한 조회 쿼리
4. domain/freepost/service/FreePostFlushService.java  → REQUIRES_NEW, 0행 처리
5. job/ranking/FreePostRankingTasklet.java           → 패턴 A (Redis 캐시 갱신)
6. job/viewcount/ViewCountFlushTasklet.java          → 패턴 B (Redis 버퍼 → MySQL 플러시)
7. job/ranking/FreePostRankingJobConfig.java         → JobConfig 구조
8. scheduler/BatchScheduler.java                    → 수동 생성자 + @Qualifier + @Scheduled
```

---

> 문의: 팀 채널 또는 GitHub Issues에 남겨주세요.
> 관련 문서: [ARCHITECTURE.md](../ARCHITECTURE.md) · [RUNBOOK.md](../RUNBOOK.md) · [ADR-001](../ADR-001-spring-batch-separation.md)
