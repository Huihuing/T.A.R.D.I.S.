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



### 4. Render free 런타임 자원 관측

2026-09-22 운영 배포 `f09bc480...`의 Render metrics 기준:

- CPU limit: 약 `0.15 CPU`
- 새 인스턴스 기동 중 CPU usage가 limit에 약 2분 가까이 붙음
- 메모리 usage: 기동 후 약 `293~297 MB`
- 메모리 limit: 약 `512 MB`
- Spring Boot 기동 완료: 약 `159.9초`
- Hikari의 첫 Aiven MySQL 연결 구간: 약 10초

따라서 현재 긴 기동시간의 1순위 병목은 메모리 부족이 아니라 free 인스턴스의 낮은 CPU 한도입니다.
Oregon ↔ Bangalore 리전 차이도 DB round trip 비용을 추가하지만 전체 160초를 설명하는 유일한 원인은 아닙니다.

운영 안정성을 우선하므로 다음 설정은 벤치마크 없이 임의 적용하지 않습니다.

- `spring.main.lazy-initialization=true`: 기동 실패를 첫 요청 시점으로 미룰 수 있음
- JPA repository lazy bootstrap: 첫 요청 지연/오류를 늦게 발견할 수 있음
- JVM tiered compilation 제한: 기동은 빨라질 수 있지만 정상 트래픽 처리량을 낮출 수 있음
- `ddl-auto=validate` 강제 전환: 실제 Flyway baseline이 아직 없어 스키마 검증 실패 위험이 있음

기동시간 자체가 운영상 문제가 되면 먼저 더 높은 CPU 플랜에서 동일 빌드를 비교하거나,
별도 staging에서 JVM/JPA 최적화를 A/B 검증한 뒤 적용합니다.

## Render 권장 설정

- Health Check Path: `/actuator/health/readiness`
  - readiness는 Spring `readinessState`만 확인
  - 공유 외부 DB 장애가 Render의 실행 중 재시작 루프로 이어지지 않도록 DB는 단일 Render health check에서 제외
  - DB 상태는 `/actuator/health` 또는 `/actuator/health/db`로 별도 관찰
- Auto Deploy: main
- Environment secrets are managed in Render, not Git.
- `FLYWAY_ENABLED=false` until the real Aiven schema baseline is verified.
- Current JPA default remains `ddl-auto=update` until the Flyway transition checklist is completed.

## Vercel 상태

Frontend builds can be blocked by the Vercel account build-rate-limit.
When this occurs:

- Render backend deployments continue independently.
- Do not treat the Vercel check failure as a frontend compile failure without inspecting the deployment.
- Frontend/CSP changes are not considered production-active until a READY Vercel production deployment contains the target Git commit.

## 다음 인프라 작업

1. Render Dashboard에서 Health Check Path를 `/actuator/health/readiness`로 지정
2. 프론트 변경 묶음이 준비되고 Vercel build-rate-limit이 허용될 때만 수동 Production deployment
3. 최신 프론트에서 CSP Report-Only violation 수집
4. 위반이 없거나 필요한 source가 정리된 뒤 enforced CSP 전환 검토
5. 실제 트래픽/비용 요구가 생기면 Render CPU 플랜 또는 리전 재배치를 staging에서 비교
6. 장기적으로 backend와 DB 리전을 동일하거나 가까운 지역으로 통합
7. 실제 Aiven schema dump를 확보한 뒤 Flyway baseline 작업 진행
