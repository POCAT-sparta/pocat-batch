# 조회수 Flush — 거래글 추가 & 전체 Bulk 전환 설계

작성일: 2026-06-16
대상 레포: `pocat-batch` (주), `pocat` (메인앱, 키 정합성 수정)

## 1. 배경 / 문제

메인앱은 조회/댓글 이벤트를 Redis ZSet 버퍼에 누적하고, 배치(`viewCountFlushJob`, 1분 간격)가
이를 DB에 반영한다. 조사 결과 다음이 확인됐다.

1. **자유글 조회수/댓글수 flush는 이미 구현·스케줄링돼 있다.**
   - `ViewCountFlushTasklet` → `FreePostFlushService` → `FreePostRepository`
   - `BatchScheduler.runViewCountFlush()` `@Scheduled(fixedDelay=60_000)` + ShedLock
2. **거래글(tradepost) 조회수 flush는 배치에 존재하지 않는다.** 메인앱 `ViewCountService`가
   `view:buffer`에 계속 누적하지만 소비자가 없다.
3. **Redis 키 불일치(잠재 버그).** 메인앱·배치 모두 **Redis Cluster**를 사용한다
   (`useClusterServers`). 배치는 `RENAME buffer → processing` 패턴을 쓰므로 두 키가 같은
   해시 슬롯에 있어야 하고, 이를 위해 **해시태그 중괄호 `{...}`** 를 사용한다.

   | 용도 | 메인앱이 쓰는 키 | 배치가 읽는 키 |
   |---|---|---|
   | 자유글 조회수 | `view:free:buffer` | `{view:free}:buffer` |
   | 자유글 댓글수 | `comment:free:buffer` | `{comment:free}:buffer` |
   | 거래글 조회수 | `view:buffer` | (없음) |

   클러스터에서 `view:free:buffer`와 `{view:free}:buffer`는 서로 다른 슬롯/키이므로
   **현재 자유글 flush는 실질적으로 동작하지 않으며**, 구 키에 카운트가 backlog로 쌓여 있다.
4. **현재 반영 방식은 row 단위 UPDATE**(`flushKey`의 per-entry 루프, `REQUIRES_NEW` 트랜잭션도
   row마다)다. 요청대로 "한 번에 처리"가 아니다.

## 2. 목표

- 거래글 조회수 flush를 배치에 추가한다.
- 자유글 조회수·댓글수 + 거래글 조회수 모두를 **bulk(JDBC batch) 1회 처리**로 전환한다.
- 메인앱 키를 배치의 해시태그 컨벤션에 맞춰 정합성을 회복한다.
- 구 키에 남은 backlog를 **1회성 drain 후 제거**한다.

비목표: 새 잡/스케줄 추가(기존 `viewCountFlushJob` 1분 주기 재사용), dedup 로직 변경,
랭킹 쿼리 변경.

## 3. 성능 결정 (요청한 조언)

"단일 `UPDATE ... CASE WHEN id THEN ...` 한 방 쿼리"는 채택하지 않는다.
- JDBC batch 대비 속도 이득이 미미하다.
- SQL이 동적으로 길어지고 가독성/유지보수가 나쁘며, 단일 거대 UPDATE는 락 구간이 길다.

**채택: `JdbcTemplate.batchUpdate` + JDBC URL `rewriteBatchedStatements=true`.**
ZSet 엔트리를 `Map<postId, delta>`로 모아 `UPDATE ... WHERE id = ?`를 배치로 묶으면
한 번의 네트워크 라운드트립/단일 트랜잭션으로 처리되며 SQL은 단순 파라미터 바인딩으로 유지된다.

## 4. 설계

### 4.1 Redis 키 표준화 (메인앱 `pocat` 수정)

writer를 reader(배치)의 해시태그 컨벤션에 맞춘다.

| 서비스 | 상수 | 변경 후 |
|---|---|---|
| `FreePostViewCountService.BUFFER_KEY` | `view:free:buffer` | `{view:free}:buffer` |
| `FreePostCommentCountService.BUFFER_KEY` | `comment:free:buffer` | `{comment:free}:buffer` |
| `ViewCountService.BUFFER_KEY` (tradepost) | `view:buffer` | `{view:trade}:buffer` |

- dedup 키(`view:free:dedup:...`, `view:dedup:...`)는 단일 키 연산이라 그대로 둔다.
- 거래글 키는 자유글(`{view:free}`)과 대칭이 되도록 `{view:trade}`로 명명한다.

### 4.2 Bulk 리포지토리 (`pocat-batch` 신규)

`ViewCountBulkRepository` — `JdbcTemplate` 기반. 테이블/컬럼은 메서드마다 하드코딩(인젝션 방지),
`id`/`delta`만 파라미터로 바인딩. 각 메서드는 `@Transactional(propagation = REQUIRES_NEW)`로
버퍼 종류 간 롤백을 격리한다.

```
int[] increaseFreePostViewCount(Map<Long,Integer> deltas)
        // UPDATE free_posts SET view_count = view_count + ? WHERE id = ?
int[] increaseFreePostCommentCount(Map<Long,Integer> deltas)
        // UPDATE free_posts SET comment_count = GREATEST(0, comment_count + ?) WHERE id = ?
int[] increaseTradePostViewCount(Map<Long,Integer> deltas)
        // UPDATE trade_posts SET view_count = view_count + ? WHERE id = ?
```

구현은 `jdbcTemplate.batchUpdate(sql, BatchPreparedStatementSetter)`. 반환된 `int[]`로
update count 0(=삭제된 글)을 식별해 경고 로그를 남길 수 있다(예외 아님 → 재시도 대상 아님).

테이블: `free_posts(view_count, comment_count)`, `trade_posts(view_count)` (확인 완료).
기존 `FreePostFlushService`와 `FreePostRepository`의 per-row `@Modifying` 메서드
(`increaseViewCount`, `updateCommentCount`)는 제거하고 bulk 리포지토리로 대체한다.
랭킹용 `FreePostRepository.findTopByPopularScore`는 유지한다.

### 4.3 Tasklet 리팩토링 (`ViewCountFlushTasklet`)

검증된 기존 골격은 유지한다.
- `flushBuffer(processingKey, bufferKey, type)`: `:failed` 재병합 → 이전 미처리 `processing`
  복구 → `RENAME buffer → processing` → `flushKey`.
- 실패 격리/재시도: 처리 실패분을 `<processingKey>:failed` ZSet에 누적, 24h TTL, 다음 회차 재병합.

변경점:
1. `FlushType`에 `TRADE_VIEW` 추가. 거래글 버퍼 키
   `{view:trade}:buffer` / `{view:trade}:buffer:processing` 추가.
2. `execute()`에 거래글 flush 추가:
   ```
   runSafely("flushFreeView",    () -> flushBuffer(FREE_PROCESSING_KEY,    FREE_BUFFER_KEY,    FREE_VIEW));
   runSafely("flushFreeComment", () -> flushBuffer(FREE_COMMENT_PROCESSING_KEY, FREE_COMMENT_BUFFER_KEY, FREE_COMMENT));
   runSafely("flushTradeView",   () -> flushBuffer(TRADE_PROCESSING_KEY,   TRADE_BUFFER_KEY,   TRADE_VIEW));
   ```
3. `flushKey`: per-entry 루프 → **엔트리를 `Map<Long,Integer>`로 수집 후 bulk 1회 호출**.
   - 파싱 불가/`null` 엔트리는 스킵(기존과 동일).
   - bulk 호출이 예외를 던지면 **수집한 엔트리 전체**를 `:failed` ZSet으로 보내고(기존 패턴),
     `processing` 키는 그대로 두지 않고 정리한다(다음 회차 재병합으로 복구).
   - 정상 시 `processing` 키 삭제.

### 4.4 구 키 backlog 1회성 drain

배포 순서: **메인앱(신규 키) 먼저 배포 → 구 키가 정적이 된 뒤 배치가 drain.** 이렇게 하면
read와 delete 사이 레이스가 없다.

- 클러스터에서 구 un-tagged 키는 rename 패턴을 쓸 수 없다(CROSSSLOT). 단일 키 연산만 가능하므로
  **read(rangeWithScores) → 신규 태그 키로 `incrementScore` 병합 → 구 키 `delete`** (best-effort)로 처리.
- 대상: `view:free:buffer` → `{view:free}:buffer`, `comment:free:buffer` → `{comment:free}:buffer`,
  `view:buffer` → `{view:trade}:buffer`.
- tasklet 시작부에서 구 키 존재 시 1회 drain. 구 키가 비워지면(삭제) 이후엔 아무도 쓰지 않으므로
  자연히 no-op가 된다. 안정화 후 후속 PR에서 drain 코드 제거.

### 4.5 설정

- 배치 datasource JDBC URL(`${DB_URL}`, AWS Parameter Store)에
  **`rewriteBatchedStatements=true`** 포함 여부 확인/추가. (batchUpdate 효과의 전제)

## 5. 데이터 흐름

```
[메인앱] 조회/댓글 이벤트
   → setIfAbsent(dedup, TTL 10m)  // 중복 방지(변경 없음)
   → ZINCRBY {view:trade}:buffer <postId> +1   // 키만 해시태그로 변경

[배치 1분 주기]  ViewCountFlushTasklet
   (최초 1회) 구 키 drain → 신규 키 병합/삭제
   for each buffer (free_view, free_comment, trade_view):
     :failed 재병합 → 이전 processing 복구
     RENAME buffer → processing
     flushKey: ZSet 전량 read → Map<id,delta> 수집
       → ViewCountBulkRepository.batchUpdate (REQUIRES_NEW, 1 round-trip)
       → 성공: processing 삭제 / 실패: 전량 :failed 누적
```

## 6. 에러 처리

| 상황 | 처리 |
|---|---|
| 삭제된 글(update count 0) | 경고 로그, 재시도 안 함(기존 동작) |
| bulk UPDATE 예외(DB 장애 등) | 수집 엔트리 전량 `:failed`(24h TTL) → 다음 회차 재병합 |
| 한 버퍼 실패 | `runSafely`로 격리, 다른 버퍼는 정상 처리 |
| 배치 중복 실행 | ShedLock(`viewCountFlushJob`)으로 단일 인스턴스 보장(기존) |

## 7. 테스트

- `ViewCountBulkRepository` 단위/슬라이스 테스트: 다건 delta 누적, `GREATEST(0,...)` 클램프,
  존재하지 않는 id에서 count 0.
- `ViewCountFlushTasklet` 테스트: 거래글 경로 추가, bulk 수집·1회 호출 검증, 실패 시 `:failed`
  누적, rename/복구 경로(기존 테스트 유지·확장).
- `ViewCountFlushJobConfigTest` 갱신.

## 8. 영향 / 마이그레이션 체크리스트

- [ ] 메인앱 3개 `BUFFER_KEY` 상수 해시태그로 변경 후 배포(먼저).
- [ ] 배치: bulk 리포지토리 추가, tasklet 거래글+bulk 전환, drain 추가, 테스트.
- [ ] JDBC URL `rewriteBatchedStatements=true` 확인.
- [ ] 배포 후 구 키 drain 완료 확인 → 후속 PR에서 drain 코드 제거.
