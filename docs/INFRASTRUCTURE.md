# T.A.R.D.I.S. 운영 인프라 / Infrastructure

## 현재 구성

- Frontend: Vercel
  - Project: tardistock
  - Production frontend uses same-origin `/api` rewrites to Render.
- Backend: Render Web Service
  - Region: Oregon
  - Runtime: Docker
  - Single instance
  - Actuator probes: `/actuator/health/liveness`, `/actuator/health/readiness`
  - Render Health Check Path: 현재 미설정(TCP check 상태)
- Database: Aiven MySQL 8.4
  - Service: `mysql-211993d1`
  - Cloud: DigitalOcean Bangalore (`do-blr`)
  - TLS required
  - Single-node free plan
  - Automatic backups enabled
  - Slow query log enabled
- Source of truth: GitHub `Huihuing/T.A.R.D.I.S.`

## 확인된 운영 특성

### 1. Backend ↔ DB 리전 불일치

현재 Render는 Oregon, Aiven MySQL은 Bangalore에 있습니다.

이 구조는 모든 DB round trip에 장거리 네트워크 지연을 추가할 수 있습니다.
코드 최적화와 별개로 로그인, 게시판, 거래, 알림 등 DB 의존 요청의 체감 속도에 영향을 줄 수 있습니다.

### 2. 리전 이동은 즉시 수행하지 않음

운영 DB 리전 변경은 단순 설정 변경이 아니라 데이터 이전, 연결 문자열 변경,
다운타임/일관성 검증이 필요한 작업이므로 자동으로 수행하지 않습니다.

이전 시 권장 순서:

1. 새 DB 또는 새 backend를 동일/인접 리전에 준비
2. 운영 DB schema-only dump와 전체 백업 확보
3. 데이터 복제 또는 dump/restore 검증
4. staging 환경에서 latency와 기능 테스트
5. Render/Aiven 연결 문자열 전환
6. 로그인, 거래, 송금, 지정가 주문, 알림, 게시판 회귀 테스트
7. 충분한 안정화 후 기존 인프라 정리

### 3. DB 접근 제어

Aiven free service의 현재 IP filter는 public ingress를 허용합니다.
Render free service는 고정 outbound IP 전제가 없으므로 단순 allowlist 축소는
backend 연결 장애를 일으킬 수 있습니다.

따라서 현재는 다음 방어를 유지합니다.

- TLS required
- DB credentials are environment variables only
- credentials are not committed to Git
- application endpoints do not expose DB connection information

고정 outbound IP 또는 private networking을 사용할 수 있는 플랜으로 전환할 때
Aiven IP allowlist를 좁히는 것을 우선 검토합니다.

### 4. Render free 런타임 자원과 cold-start 관측

Render metrics/logs에서 확인된 free 인스턴스의 주요 특성:

- CPU limit: 약 `0.15 CPU`
- 메모리 limit: 약 `512 MB`
- 일반 실행 메모리: 대략 300 MB 전후
- Aiven MySQL 첫 Hikari 연결: 최근 기동에서 약 5초, 느린 구간에서는 약 10초
- 가장 큰 병목은 메모리 부족보다 낮은 CPU 한도에서의 Spring/JPA 초기화

JPA repository bootstrap mode A/B 관측 결과는 다음과 같습니다.

- `DEFAULT`: 반복 기동 약 `153.9~165.5초`
- `LAZY`: 반복 기동 약 `69.1~83.0초`
- 최근 `LAZY` 기동 예: `71.0초`, `73.3초`, `75.1초`

현재 Render free 환경에서는 이 차이가 커서 repository bootstrap을 `LAZY`로 유지합니다.
운영 환경변수는 다음 property에 대응합니다.

```text
SPRING_DATA_JPA_REPOSITORIES_BOOTSTRAP_MODE=lazy
```

애플리케이션 설정은 이 값을 명시적으로 노출하되, Render 환경변수가 없는 로컬/기본 실행에서는
Spring Data의 보수적인 `default` 동작을 유지합니다.

```yaml
spring:
  data:
    jpa:
      repositories:
        bootstrap-mode: ${SPRING_DATA_JPA_REPOSITORIES_BOOTSTRAP_MODE:default}
```

`LAZY`는 repository proxy 자체의 초기화와 검증을 첫 repository 사용 시점까지 미룰 수 있습니다.
따라서 `/actuator/health/readiness`가 `UP`이라고 해서 모든 repository query가 이미 초기화·검증되었다는 뜻은 아닙니다.
이 trade-off는 Render free의 매우 긴 cold-start를 줄이기 위해 의도적으로 허용합니다.

이를 보완하기 위해 다음 안전장치를 유지합니다.

- Render Docker build에서 frontend production build 실행
- Render Docker build에서 전체 backend test + `bootJar` 실행
- 배포 후 public/protected API smoke test 사용
- repository/query 변경은 테스트 없이 운영에 바로 넣지 않음
- 실제 DB schema 변경은 이미 적용된 Flyway migration을 수정하지 않고 새 migration으로만 수행

운영 안정성을 위해 다음 설정은 별도 검증 없이 추가 적용하지 않습니다.

- `spring.main.lazy-initialization=true`: 전체 bean 기동 실패를 첫 요청 시점으로 과도하게 미룰 수 있음
- JVM tiered compilation 제한 추가/변경: 기동은 빨라질 수 있지만 정상 트래픽 처리량을 낮출 수 있음
- Hibernate `ddl-auto=update/create/create-drop` 복귀: 현재 Flyway + `ddl-auto=validate` 운영 원칙을 깨뜨림
- AOT 강제 전환: build-time bean 조건 고정 영향 검증이 선행되어야 함

### 5. Repository LAZY와 readiness의 경계

현재 readiness probe는 Spring `readinessState`를 사용합니다.
이는 애플리케이션 lifecycle 관점에서 트래픽 수신 가능 여부를 확인하는 용도이며,
`LAZY` repository 전체를 미리 호출해 검증하는 endpoint가 아닙니다.

따라서 첫 DB 의존 API가 repository 초기화 비용을 일부 부담할 수 있습니다.
이를 숨기기 위해 readiness endpoint에서 모든 repository에 인위적인 query를 날리지는 않습니다.
그 방식은 health probe가 실제 비즈니스 DB 부하와 결합되고 장애 시 재시작 루프를 만들 수 있기 때문입니다.

향후 유료 CPU 또는 동일 리전 DB로 이전해 cold-start 여유가 충분해지면
`DEFAULT`로 되돌린 뒤 기동시간과 첫 요청 latency를 다시 비교합니다.

## Render 권장 설정

- Health Check Path: `/actuator/health/readiness`
  - readiness는 Spring `readinessState`만 확인
  - 공유 외부 DB 장애가 Render의 실행 중 재시작 루프로 이어지지 않도록 DB는 단일 Render health check에서 제외
  - DB 상태는 `/actuator/health` 또는 `/actuator/health/db`로 별도 관찰
- Auto Deploy: main
- Environment secrets are managed in Render, not Git.
- `SPRING_DATA_JPA_REPOSITORIES_BOOTSTRAP_MODE=lazy`
  - Render free cold-start 완화용 운영 override
  - 로컬/기본값은 `default`
- Flyway: enabled
- Flyway schema version: `8`
- Flyway `baseline-on-migrate`: `false`
- Hibernate `ddl-auto`: `validate`
- 이미 적용된 `V1`~`V8` migration은 수정하지 않고 다음 스키마 변경은 `V9__...` 이상의 새 migration으로 추가

DB 마이그레이션의 상세 기준은 `docs/DB_MIGRATION.md`를 우선합니다.

## Vercel 상태

Frontend builds can be blocked by the Vercel account build-rate-limit.
When this occurs:

- Render backend deployments continue independently.
- Do not treat the Vercel check failure as a frontend compile failure without inspecting the deployment.
- Frontend/CSP changes are not considered production-active until a READY Vercel production deployment contains the target Git commit.
- 사용자 명시 승인 전에는 Vercel Production deployment를 생성하지 않습니다.

## 다음 인프라 작업

1. Render Dashboard에서 Health Check Path를 `/actuator/health/readiness`로 지정
2. Render Build Filter를 검토해 문서-only 변경이 불필요한 backend deployment를 만들지 않도록 설정 가능한지 확인
3. 프론트 변경 묶음이 준비되고 사용자가 승인했을 때만 수동 Vercel Production deployment
4. 최신 프론트에서 CSP Report-Only violation 수집
5. 위반이 없거나 필요한 source가 정리된 뒤 enforced CSP 전환 검토
6. 실제 트래픽/비용 요구가 생기면 Render CPU 플랜 또는 리전 재배치를 staging에서 비교
7. 장기적으로 backend와 DB 리전을 동일하거나 가까운 지역으로 통합
8. DB 스키마 후속 변경은 `docs/DB_MIGRATION.md` 규칙에 따라 `V9+` migration으로 진행
