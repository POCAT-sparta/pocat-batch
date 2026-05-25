# pocat-batch

POCAT 메인 앱의 `@Scheduled` 스케줄러를 별도 서버로 분리한 Spring Batch 샘플 레포.
멀티 인스턴스 배포 환경에서 스케줄러 중복 실행을 방지하기 위해 구축되었다.

> **참고**: 이 레포는 팀원 참고용 샘플 구현이다. 실제 운영 전환 전에 ShedLock 도입 및 메인 앱 스케줄러 폐기 계획을 검토해야 한다.

---

## 메인 앱과의 관계

| 항목 | 내용 |
|------|------|
| DB | POCAT 메인 앱과 동일한 MySQL DB 공유 |
| 엔티티 | 메인 앱 엔티티 복제 (`FreePost`, `BaseEntity`) |
| Redis | 동일 Redis 인스턴스 사용 |
| 병행 운영 | 초기에는 메인 앱 스케줄러와 병행 실행됨 (중복 주의) |

---

## 구현된 Job

### 1. `freePostRankingJob`

자유게시판 인기 랭킹을 Redis ZSet으로 갱신하는 Job.

- **Tasklet**: `FreePostRankingTasklet`
- **동작**: `FreePostRepository.findTopByPopularScore` 조회 → Redis `ranking:free:popular` ZSet RENAME
- **스케줄**: `fixedDelay=60s` (이전 실행 완료 후 60초)

### 2. `viewCountFlushJob`

Redis 버퍼에 누적된 조회수·댓글수를 MySQL DB에 플러시하는 Job.

- **Tasklet**: `ViewCountFlushTasklet`
- **동작**: `FreePostFlushService.increaseViewCount` + `updateCommentCount` (`@Transactional REQUIRES_NEW`)
- **스케줄**: `fixedDelay=60s`

---

## 빠른 시작

### 전제조건

- Java 17+
- MySQL 실행 중 (POCAT 메인 앱 DB 접근 가능)
- Redis 실행 중

### 환경 변수 설정

프로젝트 루트에 `.env` 파일 생성 (`.env.example` 참고):

```bash
cp .env.example .env
# .env 파일에서 DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT 수정
```

### 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

timezone 설정이 필요한 경우:

```bash
./gradlew bootRun -Duser.timezone=Asia/Seoul --args='--spring.profiles.active=local'
```

최초 실행 시 Spring Batch 메타테이블(`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION` 등)이 자동 생성된다.

---

## 향후 작업

- **ShedLock 도입**: 다중 배치 인스턴스 배포 시 중복 실행 방지 (현재는 단일 인스턴스 전제)
- **다른 도메인 Job 추가 방법**:
  1. `job/{domain}/` 패키지에 `*JobConfig`, `*Tasklet` 클래스 추가
  2. `BatchScheduler`에 `JobLauncher.run()` 호출 메서드 추가
  3. 필요 시 `domain/{도메인}/` 패키지에 엔티티·레포지토리 복제
- **메인 앱 스케줄러 단계적 폐기**: `FreePostRankingScheduler`, `ViewCountFlushScheduler` 비활성화 또는 삭제

---

## 관련 문서

- [`docs/guide/batch-guide.md`](docs/guide/batch-guide.md) — **팀원 대상 개발 가이드 (새 Job 추가 방법 · 동작 원리)**
- [`docs/ADR-001-spring-batch-separation.md`](docs/ADR-001-spring-batch-separation.md) — 분리 결정 배경
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — 패키지 구조·Job 흐름·Redis 키 표
- [`docs/RUNBOOK.md`](docs/RUNBOOK.md) — 실행·운영 가이드
