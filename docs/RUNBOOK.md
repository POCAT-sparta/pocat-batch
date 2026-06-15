# RUNBOOK — pocat-batch

---

## 로컬 실행 전제조건

| 항목 | 요구사항 |
|------|----------|
| Java | 17 이상 |
| MySQL | 로컬에서 실행 중 (POCAT 메인 앱 DB 접근 가능) |
| Redis | 로컬에서 실행 중 (`localhost:6379` 기본) |
| Gradle | Wrapper 포함 (`./gradlew` 사용) |

> MySQL과 Redis가 실행되어 있지 않으면 애플리케이션 시작 시 연결 오류로 즉시 종료된다.

---

## .env 파일 작성

프로젝트 루트에 `.env` 파일을 생성한다. `.env.example`을 복사해서 시작한다.

```bash
cp .env.example .env
```

`.env` 파일 항목 설명:

| 환경 변수 | 설명 | 예시 |
|-----------|------|------|
| `DB_URL` | MySQL JDBC URL (POCAT 메인 DB) | `jdbc:mysql://localhost:3306/pocat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` |
| `DB_USERNAME` | MySQL 사용자명 | `root` |
| `DB_PASSWORD` | MySQL 비밀번호 | `password` |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `REDIS_PORT` | Redis 포트 | `6379` |
| `BATCH_SERVER_PORT` | 배치 서버 HTTP 포트 (메인 앱과 충돌 방지) | `8081` |
| `POCAT_API_BASE_URL` | POCAT 메인 백엔드 base URL (`Main*Client`가 internal API 호출 시 사용) | `http://localhost:8080` |
| `POCAT_INTERNAL_TOKEN` | POCAT 메인 백엔드 internal API 인증 토큰 (`X-Internal-Token` 헤더) | (메인 백엔드와 동일한 값으로 설정) |

---

## 최초 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

timezone 명시가 필요한 경우:

```bash
./gradlew bootRun -Duser.timezone=Asia/Seoul --args='--spring.profiles.active=local'
```

### 최초 실행 확인 로그

Spring Batch가 MySQL에 메타테이블을 자동 생성한다. 아래와 같은 로그가 출력되면 정상이다.

```text
Executing SQL script from class path resource [org/springframework/batch/core/schema-mysql.sql]
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

MySQL에서 `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION` 등 테이블이 생성된 것을 확인할 수 있다:

```sql
SHOW TABLES LIKE 'BATCH_%';
```

---

## 수동 Job 실행

현재 구현에서 Job은 `BatchScheduler`가 `@Scheduled(fixedDelay=60s)`로 자동 실행한다.
별도의 HTTP 엔드포인트나 CLI 트리거는 제공하지 않는다.

**수동으로 즉시 실행하려면**: 애플리케이션을 재시작하면 시작 60초 후 첫 번째 Job이 실행된다.

---

## 등록된 스케줄 Job

### auctionActivationJob (#235)

- **실행 주기**: 매일 19:00 (Asia/Seoul) — `cron = "0 0 19 * * *"`
- **중복 실행 방지**: ShedLock 적용 (`lockAtMostFor = PT30M`)
- **동작**: DB에서 `APPROVED` 상태 경매 목록을 조회한 뒤, 건별로 메인앱 `POST /internal/auctions/{id}/activate` 호출. 응답 `data: true` → 활성화 성공, `false` → 스킵.
- **주요 변경 (#235)**: 기존 배치 서버 내 자체 도메인 로직 제거 → 메인앱 Internal API 위임으로 전환.
- **의존성**: 메인앱 POCAT 먼저 배포 필요 (internal API 엔드포인트 존재 확인).
- **재시도**: 5xx·네트워크 오류 시 지수 백오프 최대 3회. 4xx 오류는 즉시 RuntimeException → 해당 경매 스킵.
- **오류 진단**:
  - 401 → `POCAT_INTERNAL_TOKEN` 환경변수와 메인앱 설정값 일치 여부 확인
  - 500 → 메인앱 로그 확인 (`/internal/auctions/{id}/activate`)
- **모니터링 포인트**:
  - 배치 로그의 `활성화={}, 스킵={}, 실패={}` 카운터
  - `failedCount > 0` 시 메인앱 경매 서비스 이상 여부 점검

---

### auctionExpirationJob (#235)

- **실행 주기**: 매일 19:05~19:30 매분 (Asia/Seoul) — `cron = "0 5-30 19 * * *"`
- **중복 실행 방지**: ShedLock 적용 (`lockAtMostFor = PT50S`)
- **동작**: DB에서 `ACTIVE` 상태이고 `endedAt <= now()`인 경매 목록을 조회한 뒤, 건별로 메인앱 `POST /internal/auctions/{id}/close-expired` 호출.
- **주요 변경 (#235)**: 기존 배치 서버 내 자체 도메인 로직 제거 → 메인앱 Internal API 위임으로 전환.
- **의존성**: 메인앱 POCAT 먼저 배포 필요.
- **재시도**: 5xx·네트워크 오류 시 지수 백오프 최대 3회. 4xx 오류는 즉시 RuntimeException → 해당 경매 스킵.
- **오류 진단**:
  - 401 → `POCAT_INTERNAL_TOKEN` 환경변수 확인
  - 500 → 메인앱 로그 확인 (`/internal/auctions/{id}/close-expired`)
- **모니터링 포인트**:
  - 배치 로그의 `종료={}, 스킵={}, 실패={}` 카운터
  - `failedCount > 0` 연속 발생 시 메인앱 경매 만료 처리 로직 점검

---

### cardSyncJob (#235)

- **실행 주기**: 매주 일요일 00:00 (Asia/Seoul) — `cron = "0 0 0 * * SUN"`
- **중복 실행 방지**: ShedLock 적용 (`lockAtMostFor = PT2H`)
- **동작**: 메인앱 `POST /internal/cards/sync` 트리거 요청 1회 발송. 실제 카드 동기화는 메인앱 `syncExecutor`에서 비동기 실행.
- **주요 변경 (#235)**: 기존 배치 서버 내 자체 카드 동기화 로직 제거 → 메인앱 Internal API 위임으로 전환.
- **의존성**: 메인앱 POCAT 먼저 배포 필요.
- **특이사항 (정상 동작)**:
  - `202 Accepted` → 정상 트리거 완료 (비동기 처리 시작됨)
  - `409 CARD_SYNC_IN_PROGRESS` → 이미 동기화 진행 중 — **정상 스킵** (오류 아님)
  - `4xx` (401 제외) → 경고 로그 후 스킵
- **오류 진단**:
  - 401 → `POCAT_INTERNAL_TOKEN` 환경변수 확인. 이 경우 RuntimeException 발생 → Job FAILED.
  - 500 → 메인앱 로그 확인 (`/internal/cards/sync`)
- **모니터링 포인트**:
  - 실제 동기화 완료 여부는 메인앱 로그의 `syncExecutor` 스레드 추적
  - 409 반복 시 메인앱에서 이전 동기화가 완료되지 않은 것 → 메인앱 syncExecutor 처리 시간 점검

---

### aiReindexJob (ADR-018, #222)

- **실행 주기**: 매일 01:00 (Asia/Seoul)
- **중복 실행 방지**: ShedLock 적용
- **동작**: ACTIVE 카드 ID를 cursor 기반으로 100개씩 청크로 묶어 POCAT 메인 백엔드 `/internal/ai/reindex-cards`에 위임. ES(`pocat-ai-index`)에 이미 인덱싱된 카드는 스킵.
- **조기 종료**: 메인 백엔드 응답에서 Gemini rate-limit(`rateLimited=true`) 도달 시 해당 실행을 조기 종료한다. 미처리 카드는 다음 실행에서 자가치유(self-healing)된다.
- **모니터링 포인트**:
  - Gemini API 사용량 및 `rateLimited` 도달 빈도
  - ES `pocat-ai-index` 인덱싱된 카드 수 증가 추이

---

## 운영 주의사항

### 병행 운영 기간

배치 서버 도입 초기에는 메인 앱의 `FreePostRankingScheduler`, `ViewCountFlushScheduler`와 병행 실행된다.
이 기간 중 동일 작업이 2회 수행되므로 **조회수·댓글수 중복 플러시 여부**를 모니터링해야 한다.

메인 앱 스케줄러를 비활성화하려면:
- `@Scheduled` 어노테이션 제거 또는
- 해당 스케줄러 클래스에 `@Profile("!prod")` 추가로 운영 프로파일에서 제외

### BATCH_* 테이블 스키마 분리

운영 환경에서는 `BATCH_*` 메타테이블을 별도 스키마(예: `batch`)에 분리 운영하는 것을 권장한다.

```yaml
# application-prod.yml 예시
spring:
  batch:
    jdbc:
      schema: always
  datasource:
    batch:
      url: jdbc:mysql://prod-db:3306/batch?serverTimezone=Asia/Seoul
```

### timezone 설정

JVM 레벨에서 timezone을 명시하지 않으면 서버 OS 설정을 따른다. 운영 서버에서 KST 기준 스케줄 로그를 확인하려면 JVM 옵션에 다음을 추가한다:

```text
-Duser.timezone=Asia/Seoul
```

### 다중 배치 인스턴스 운영 시

현재 구현은 **단일 인스턴스 전제**이다. 다중 인스턴스 배포 시 ShedLock 또는 Quartz Cluster 도입이 필요하다. ADR-001 참고.

---

## 운영 배포 체크리스트

배포 전 아래 항목을 반드시 확인할 것:

- [ ] `.env` 파일이 Git에 커밋되지 않았는가? (`.gitignore` 확인)
- [ ] `application.yaml`의 `initialize-schema: never` 로 변경했는가? (운영 DB 스키마 보호)
- [ ] Redis `requirepass` 설정 및 `REDIS_PASSWORD` 환경변수 주입 완료했는가?
- [ ] 메인 앱(`POCAT`) 의 `FreePostRankingScheduler`, `ViewCountFlushScheduler` 비활성화 또는 삭제했는가? (중복 실행 방지)
- [ ] JVM 옵션 `-Duser.timezone=Asia/Seoul` 추가했는가?
- [ ] Spring Batch 메타테이블(`BATCH_*`) 이 DB에 수동으로 생성되었는가? (`initialize-schema: never` 사용 시)
- [ ] Actuator health 엔드포인트(`/actuator/health`)로 정상 기동 확인했는가?

### #235 Internal API 위임 전환 추가 체크리스트

- [ ] **메인앱 POCAT 먼저 배포 완료**했는가? (`auctionActivationJob`, `auctionExpirationJob`, `cardSyncJob`은 메인앱 internal API 의존)
- [ ] `POCAT_INTERNAL_TOKEN` 환경변수가 메인앱 설정과 동일한 값으로 주입됐는가?
- [ ] `POCAT_API_BASE_URL` 이 운영 메인앱 URL로 올바르게 설정됐는가?
- [ ] 첫 배포 후 19:00 경매 활성화 배치 로그에서 `401` 오류 없음 확인했는가?
- [ ] 일요일 00:00 cardSyncJob 최초 실행 후 메인앱 `syncExecutor` 로그에서 동기화 완료 확인했는가?
