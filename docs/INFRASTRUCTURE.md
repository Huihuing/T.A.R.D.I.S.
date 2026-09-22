# T.A.R.D.I.S. 운영 인프라 / Infrastructure

## 현재 구성

- Frontend: Vercel
  - Project: tardistock
  - Production frontend uses same-origin `/api` rewrites to Render.
- Backend: Render Web Service
  - Region: Oregon
  - Runtime: Docker
  - Single instance
  - Health endpoint: `/actuator/health`
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

## Render 권장 설정

- Health Check Path: `/actuator/health`
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

1. Vercel build-rate-limit 해제 후 최신 frontend production 배포 확인
2. CSP Report-Only violation 수집
3. 위반이 없거나 필요한 source가 정리된 뒤 enforced CSP 전환 검토
4. Render Health Check Path를 `/actuator/health`로 지정
5. 장기적으로 backend와 DB 리전을 동일하거나 가까운 지역으로 통합
6. 실제 Aiven schema dump를 확보한 뒤 Flyway baseline 작업 진행
