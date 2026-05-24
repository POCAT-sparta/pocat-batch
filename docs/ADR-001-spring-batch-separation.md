# ADR-001: Spring Batch 스케줄러 분리

| 항목 | 내용 |
|------|------|
| **Status** | Accepted |
| **Date** | 2026-05-24 |
| **Deciders** | POCAT 팀 |

---

## Context

POCAT 메인 앱에는 자유게시판 관련 `@Scheduled` 스케줄러가 2개 존재한다.

- `FreePostRankingScheduler` — 인기 랭킹 Redis ZSet 갱신
- `ViewCountFlushScheduler` — 조회수·댓글수 Redis 버퍼 → MySQL DB 플러시

현재 구조에서는 메인 앱 서버를 멀티 인스턴스로 스케일아웃할 경우, 각 인스턴스가 독립적으로 스케줄러를 실행하여 **동일 Job이 중복 실행**되는 문제가 발생한다. 조회수 플러시의 경우 중복 실행 시 데이터 정합성 문제로 이어질 수 있다.

---

## Decision

별도 `pocat-batch` 서버를 구축하고, 자유게시판 스케줄링 Job 2개를 **Spring Batch Tasklet 기반**으로 분리 구현한다.

- Job 실행 방식: `fixedDelay=60s` (이전 실행 완료 후 60초 대기)
- JobRepository 저장소: **MySQL `BATCH_*` 메타테이블** 채택 (실행 이력 관리 목적)
- 초기에는 메인 앱 스케줄러와 **병행 운영**하며, 안정성 확인 후 메인 앱 스케줄러를 단계적으로 폐기한다.

---

## Alternatives Considered

### ShedLock

- 메인 앱에 ShedLock 라이브러리를 추가하여 분산 잠금으로 중복 실행 방지
- **거절 이유**: 현재 태스크 범위를 초과. 별도 ADR에서 검토 예정 (다중 배치 인스턴스 대비)

### `fixedRate` / cron 표현식

- `fixedRate`는 이전 실행이 끝나지 않아도 다음 실행이 트리거됨
- **거절 이유**: 장기 실행 Job 시 백로그 누적 위험. `fixedDelay`가 더 안전함

### In-memory JobRepository

- Spring Batch 기본 설정인 `MapJobRepositoryFactoryBean` 사용
- **거절 이유**: 애플리케이션 재시작 시 실행 이력 소실. 운영 환경에서 Job 실행 이력 추적 불가

---

## Consequences

**긍정적 효과**
- 메인 앱 스케일아웃 시 스케줄러 중복 실행 문제 해소 (배치 서버는 단일 인스턴스 운영)
- Spring Batch 메타테이블을 통한 Job 실행 이력 영속 관리 가능
- 스케줄러 관련 로직을 메인 앱에서 분리하여 메인 앱 복잡도 감소

**부정적 효과 / 주의사항**
- 초기 병행 운영 기간 중 동일 작업이 메인 앱 + 배치 서버에서 각 1회씩 총 2회 수행됨
- 운영 전환 시 메인 앱의 `FreePostRankingScheduler`, `ViewCountFlushScheduler`를 반드시 비활성화(`@Scheduled` 제거 또는 클래스 삭제) 해야 함
- 향후 배치 서버를 멀티 인스턴스로 운영할 경우 ShedLock 도입 필요

---

## Related

- POCAT 메인 레포 `docs/adr/ADR-003-batch-server-extraction.md`
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — 상세 패키지 구조 및 Job 흐름
- [`RUNBOOK.md`](RUNBOOK.md) — 로컬 실행 및 운영 가이드
