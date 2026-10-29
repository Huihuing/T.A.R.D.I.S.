# PULSE//ID · T.A.R.D.I.S. 계정 연결 및 Hub SSO

> **2026-10-28 · feature 브랜치 구현 / 운영 환경 미적용**

T.A.R.D.I.S.에서 원본 `member.id`와 지갑·PIN·거래원장·일일 보상을 보존하면서 PULSE//LAB과 로그인 경로를 연결한다.

## 기존 계정의 1회 연결

- `POST /api/auth/pulse-link/proof`는 기존 Tardis JWT로 로그인한 회원의 실제 member.id에 대해 PULSE challenge/World/iss/aud/jti/iat/exp를 포함한 90초 서명 증명 발급.
- 프론트 `/pulse-link?challenge=...`에서 증명을 복사한다. 비밀번호/PIN을 PULSE 서버로 전달하지 않는다.
- 강력한 `PULSE_LINK_PROOF_SECRET`이 없으면 기능은 503으로 비활성화된다.

## 연결 후 “한 번에 입장” (Hub SSO)

- Portal이 발급하는 단회 60초 티켓을 fragment로 받은 `/pulse-sso`가 즉시 history에서 제거하고 기존 Tardis API `POST /api/auth/pulse-sso`로 전송.
- Tardis 백엔드만 중앙 PULSE `POST /api/pulse-id/sso/redeem`에 **별도 서버 비밀키**로 인증하고 원본 member ID를 가져온다.
- 백엔드는 `memberRepository.findByIdForUpdate`를 통해 회원 잠금을 확보하고 기존 `AuthController.completeLogin` 경로로 **Tardis 고유 JWT/refresh cookie**를 발급한다. 일일 보상 및 PIN 정책을 유지한다.
- 이 방식은 World별 Keycloak OIDC Relying Party가 아니라 중앙 Keycloak 로그인 + 1회용 Hub 티켓이다.

## 환경 설정 (서버 전용)

- `PULSE_LINK_PROOF_SECRET` ↔ 포털 `PULSE_LINK_TARDIS_SECRET`
- `PULSE_SSO_REDEEM_URL=https://<trusted-pulse-host>/api/pulse-id/sso/redeem`
- `PULSE_SSO_SERVICE_KEY` ↔ 포털 `PULSE_SSO_TARDIS_KEY` (proof 서명 키와 반드시 달라야 한다)
- `PULSE_SSO_ALLOW_LOCAL_HTTP=true`는 **로컬 loopback 개발 환경에서만** 허용.
- 기존 JWT/DB/SMTP/Google 비밀키/송금 PIN 시스템은 변경하지 않는다.

## 검증 및 남은 게이트

- CI: [T.A.R.D.I.S. Actions #40648731486](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40648731486) Java tests + React build **PASS**.
- Portal 합성 signed proof → 일회용 Hub ticket browser E2E는 별도 중앙 CI에서 PASS.
- 아직 실제 Tardis 테스트 DB/실회원과 중앙 Hub를 함께 실행한 E2E, 계정 정지/로그아웃 즉시 철회, 운영 설정/DB 이사는 미검증. 실제 배포 전 필수.
- **Draft PR #131 유지 / main 미병합 / 운영 배포·환경변수 변경 없음.**

참조: [PULSE//LAB SSO 통합 계약](https://github.com/Huihuing/pulse-lab/blob/feature/pulse-id-foundation/docs/PULSE_HUB_SSO.md).

## 월드에서 PULSE 로그인으로 진입 (선택 설정)

Tardis 프론트엔드에 **공개 URL만** `VITE_PULSE_LAB_URL=https://<사용 중인 PULSE 포털>`로 설정하면 기존 로그인 화면에 `PULSE//ID로 로그인` 링크가 나타난다. 로그인 완료 후 포털에서 `T.A.R.D.I.S. 한 번에 입장`을 선택하는 방식이다. 로컬 개발은 `http://localhost:3000`처럼 loopback만 허용한다. 값을 설정하지 않으면 기존 로그인 화면만 나온다. JWT/서버 비밀값은 절대 `VITE_*`에 넣지 않는다.

## 2026-10-29 실제 Tardis MySQL SSO 검증

- [GitHub Actions #40807739781](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40807739781): **SUCCESS**.
- GitHub Actions의 일회성 **MySQL 8.4**에 원본 Tardis Flyway V1–V10 마이그레이션을 적용하고, `flyway_schema_history`의 **V9 적용 완료를 먼저 확인한 뒤에만** 검증용 회원과 지갑을 생성했다. V9는 기존 회원·지갑·원장 데이터를 일괄 삭제하는 위험한 과거 일회성 마이그레이션이므로 Shadow 복원 시 이력 보존이 필수다.
- 실제 Tardis Spring Boot 서버가 로컬 테스트용 중앙 PULSE 티켓 교환 서버에 서명키와 별개인 서버 비밀키로 인증해 원래 `member.id`를 반환받고 **기존 JWT + refresh cookie**를 발급하는 것까지 확인했다.
- 발급받은 JWT로 기존 보호 API `GET /api/auth/sessions`을 호출했고, 기존 회원의 refresh 세션을 실제 DB에서 확인했다.
- 지갑 잔고가 최초 **100.00 → 600.00**이 되고 `ledger_entry`에 **일일 로그인 보상 500.00이 정확히 한 번만** 기록됨을 확인했다. 두 번째 다른 유효한 SSO 티켓으로 같은 날 로그인해도 원장/지갑 보상이 중복 발생하지 않았다.
- 이미 사용된 SSO 티켓의 재사용은 401로 거부됐다.
- **주의: 중앙 PULSE 서버는 이번 Tardis 통합 테스트에서 로컬 모의 서버다.** 실제 Keycloak+PULSE 계정 DB 브라우저 테스트와 통합한 4개 실앱 종단간 E2E는 아직 필요하다.
- 원래 운영 회원/지갑/원장을 변경하지 않았으며 CI가 폐기하는 MySQL 테스트 데이터만 사용했다.

## 2026-10-29 PULSE 전체 기기 로그아웃: 실제 MySQL 통과

- 신규 additive Flyway `V11__pulse_global_logout_generation.sql`은 원래 `member`에 `session_generation BIGINT NOT NULL DEFAULT 0`만 추가한다. **V9는 과거 계정·지갑·원장을 삭제하는 마이그레이션이므로 운영/Shadow에서 재실행 금지.**
- 기존 Tardis JWT에 서명된 `sg` (generation)을 포함하고, 보호 API 요청 시 현재 회원 세대와 비교한다. 초기 generation 0의 오래된 JWT는 호환되지만, 전체 로그아웃 시 generation을 증가시켜 이전 JWT를 즉시 거부한다.
- `POST /api/auth/pulse-sso/revoke`: World 서버 전용 `PULSE_SSO_SERVICE_KEY`를 검사하고, **원본 회원 row lock + generation 증가 + 모든 refresh 토큰 폐기**를 하나의 DB 트랜잭션으로 실행한다.
- [Tardis 실제 Spring/MySQL SSO CI #40809953724](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40809953724) **PASS**: 오래된 JWT/refresh 거부, 새 로그인 허용, 기존 지갑/원장 및 일일 보상 무중복 확인.
- [Tardis Java/Frontend PR CI #40809953939](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40809953939) **PASS**: JWT 세대 검증 및 서비스 비밀키 거부 단위 테스트.
- 주의: 이 기능은 **원래 비밀번호/Google 로그인 세션까지 모든 기기에서 종료**한다. 보호 API마다 회원 세대 DB 조회가 추가되어 부하·DB 장애 fail-closed 회귀 검증이 필요하다.
- 프로덕션 Keycloak 다른 기기 세션 종료, 장애 재시도 중복 철회, 실제 4개 World 동시 E2E 및 운영 DB 전환은 아직 완료되지 않았다. 운영 DB/회원/배포 미변경.

## 2026-10-29 멱등 글로벌 로그아웃 — 신규 V12

- `Backend/src/main/resources/db/migration/V12__pulse_logout_receipts.sql`이 기존 `member.id` FK를 유지한 상태로 `pulse_logout_receipts(operation_id,member_id)`를 추가한다.
- 중앙은 **매 글로벌 로그아웃 작업마다 안정적인 UUID `operationId`**를 발급한다. Tardis는 회원 row lock 아래 기존 receipt를 확인하고, **처음 처리한 경우에만** JWT generation 증가·모든 refresh 삭제·receipt 기록을 한 트랜잭션으로 수행한다.
- 첫 요청이 성공한 뒤 응답이 끊겨 재요청되어도 같은 `operationId`면 generation을 다시 올리지 않는다. 따라서 **새로 로그인한 세션을 중복 로그아웃으로 무효화하지 않는다**.
- [실제 Spring/MySQL 일회성 검증](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40822722241) 성공: 기존 지갑/원장·일일 보상 불변, 재시도 receipt 정확히 1개, 새 JWT 유효. 운영 V12 미적용.

## 2026-10-29 운영 DB 이전 전 Shadow 백업/복원 리허설 PASS

- [격리 MySQL 8.4 전체 복원 CI #40827975852](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40827975852): **PASS**. 검증용 회원/지갑/일일 보상/원장 데이터를 생성하고, 원본 Flyway V1~V12를 **이력 포함** `mysqldump` → 새 `tardis_shadow_ci` DB로 복원했다.
- [실제 검증 코드](../Backend/scripts/pulse-db-shadow-rehearsal.py): **20개 테이블·15개 FK, 각 행의 SHA-256, 컬럼 구조, Flyway history/checksum, 회원 PK·지갑 잔고·원장**을 복원 전후에 비교.
- 복원 DB에 실제 Spring Boot를 다시 띄워 Flyway가 기존 V9 삭제성 마이그레이션을 재실행하지 않는지 확인했고, 이후 모든 원래 행 해시를 재검증했다. `flyway_schema_history` V9만 제거한 **격리된 부정 테스트 DB는 서비스 연결 전에 거부**했다.
- **중요:** V9는 회원·지갑·원장 등 17개 테이블의 행을 삭제하는 과거 migration이므로, 실제 운영/Shadow에 역사를 빼고 V9를 재실행하는 건 허용할 수 없다.
- 테스트는 **합성 데이터만** 사용하며 실제 Aiven DB 접근·덤프·마이그레이션·배포는 하지 않았다. [세 World 복원 결과](https://github.com/Huihuing/pulse-lab/blob/feature/pulse-id-foundation/docs/DB_SHADOW_REHEARSAL_2026-10-29.md).

## 2026-10-29 이전 전 Flyway 삭제 위험 정적 검사

- `Backend/scripts/check-pulse-migration-safety.mjs`는 **DB에 연결하거나 SQL을 실행하지 않는** 정적 검사다. 기존 V9 데이터 초기화 SQL을 원본 Git blob SHA로 고정하고 V1부터 연속된 Flyway 버전 및 V9 외 DELETE/TRUNCATE/DROP을 검사한다.
- [PR CI #40831884858](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40831884858) **PASS**. [실제 폐기형 MySQL/Shadow #40831884875](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40831884875) **PASS**.
- 단, **이 검사만으로 운영 DB가 Flyway V9을 다시 실행하지 않는다고 보장하지 않는다.** `flyway_schema_history`·백업·Shadow 리허설 확인 후에만 운영 적용 가능.
