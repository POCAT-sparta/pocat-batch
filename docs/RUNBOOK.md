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

```
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

```
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
