# Agent Instructions

Claude Code, Codex 등 모든 AI 코딩 에이전트가 공유하는 작업 기준입니다.
사람용 프로젝트 소개는 `README.md`, Claude Code 전용 규칙은 `CLAUDE.md`를 봅니다.

## Project Goal

T.A.R.D.I.S.는 실시간 시장 데이터 기반 **모의투자·가상자산·커뮤니티·랭킹** 학습/포트폴리오 프로젝트입니다.

- 실제 돈이 아닌 **가상자산**만 다룹니다. 실제 입출금이나 증권 주문은 없습니다.
- 그래도 잔액·거래원장 정합성은 실제 금융처럼 다룹니다. 금액 버그는 가장 심각한 버그입니다.

## Source of Truth

우선순위: **현재 코드 > Git history > 이 문서 > 기타 문서.** 세션 기록은 source of truth가 아닙니다.

새 세션/새 PC에서는 아래 순서로만 복구하고, 충분하면 더 조사하지 않습니다.

1. `AGENTS.md` (이 문서, 특히 Current State)
2. `CLAUDE.md` (Claude Code인 경우)
3. `git status`, 현재 branch, `git log --oneline -10`
4. 현재 작업과 관련된 파일

전체 저장소를 처음부터 다시 분석하지 않습니다. 진행 기록은 `PROGRESS.md` 같은 별도 파일이 아니라 Git history와 아래 Current State로 관리합니다.

## Architecture

```text
Browser
  -> Vercel (Frontend, React/Vite)
      -> same-origin /api/* rewrite
          -> Render (Spring Boot 4.1, Docker, Oregon, free)
              -> Aiven MySQL 8.4 (Bangalore, TLS)
              -> Finnhub / Naver / FreeImage / Google APIs
```

- Frontend: React + TypeScript + Vite + Tailwind CSS
- Backend: Spring Boot 4.1 + Spring Security + Spring Data JPA, Java 21
- 운영 주소: Frontend `https://tardis-neon.vercel.app`, Backend `https://t-a-r-d-i-s.onrender.com`
- Render와 Aiven은 리전이 달라 DB round-trip 지연이 있습니다. Render free는 repository bootstrap `LAZY`로 운영합니다.

## Important Files

Backend (`Backend/src/main/java/com/tardistock/backend/`):

| 영역 | 파일 |
| --- | --- |
| 송금 / PIN / 계정 보안 | `controller/AccountController.java` |
| 매수·매도 | `controller/TradeController.java` |
| 보상 / 경제 | `service/EconomyService.java`, `controller/EconomyController.java` |
| 지정가 주문 | `service/LimitOrderService.java`, `service/LimitOrderScheduler.java` |
| 원장 / 금액 계산 | `service/LedgerService.java`, `util/MoneyMath.java`, `util/DecimalMath.java` |
| 트랜잭션 롤백 도우미 | `util/TransactionRollbackSupport.java` |
| 인증 | `controller/AuthController.java`, `security/JwtTokenProvider.java`, `security/JwtAuthenticationFilter.java`, `service/RefreshTokenService.java`, `service/GoogleIdentityService.java`, `config/SecurityConfig.java` |
| 이메일 인증번호 | `service/EmailVerificationService.java`, `service/PasswordResetService.java`, `service/EmailCodeSendQuotaService.java` |
| 커뮤니티 / 신고 | `controller/BoardController.java`, `controller/CommunityReportController.java`, `controller/AdminController.java` |
| Rate limit / client IP | `security/RequestRateLimitFilter.java`, `security/TrustedProxyHeaderFilter.java` |
| DB migration | `Backend/src/main/resources/db/migration/V*.sql` |

Frontend: `Frontend/src/auth.ts`(중앙 `authFetch`), `Frontend/src/pages/*.tsx`, `Frontend/vercel.json`.

상세 문서:

- `docs/DB_MIGRATION.md` — Flyway 기준과 migration 이력 (DB 정책은 이 문서가 우선)
- `DEPLOYMENT.md` — 로컬/Render/Vercel 설정과 환경변수
- `docs/INFRASTRUCTURE.md` — 인프라 특성과 운영 후속 작업
- `SECURITY.md` — 보안 정책

## Build / Test / Run

```powershell
# Backend 테스트 + 빌드 (Render와 동일한 검증)
cd Backend; .\gradlew.bat test bootJar --no-daemon

# Backend 로컬 실행
cd Backend; .\gradlew.bat bootRun

# Frontend
cd Frontend; npm install; npm run build
```

로컬 비밀 설정은 `Backend/src/main/resources/application-api.example.yaml`을 복사한 `application-api.yaml`(Git 제외)을 사용합니다. 백엔드 테스트는 DB 없이 Mockito로 돌아가므로 Hibernate schema validate는 Render 배포에서 처음 확인됩니다.

## Development Rules

### 금액 / 가상자산 (최우선)

- wallet, ledger, transfer, buy/sell, reward, limit order, portfolio를 바꿀 때는 **동시성과 중복 실행**을 반드시 고려합니다.
- 잔액이 바뀌는 경로는 `findBy...ForUpdate` DB write lock 안에서 처리합니다.
- 잔액 변경과 원장 기록은 같은 트랜잭션에서 함께 성공하거나 함께 롤백되어야 합니다. 원장 누락이 없어야 합니다.
- overflow·음수 잔액·precision 초과는 **변경(mutation) 전에** 거부합니다.
- 금액 경계 테스트: `NaN`, `Infinity`, 음수, 반올림 경계, DB precision 초과.
- 정밀도: Wallet / Ledger / PortfolioSnapshot / LimitOrder / PriceAlert는 `DECIMAL(19,2)`, 시장 가격(Portfolio 평균가, TradeHistory, Bookmark)은 `DECIMAL(19,6)`. 현금 반올림은 `MoneyMath.roundCents`(HALF_UP).
- 클라이언트가 보낸 가격·username을 신뢰하지 않습니다. 체결가는 서버가 조회하고 사용자는 JWT로만 식별합니다.

### 트랜잭션

- `@Transactional` 경로에서 예외를 catch해 오류 응답을 반환하면 `TransactionRollbackSupport.markRollbackOnlyIfActive()`로 부분 변경을 롤백합니다.
- 예외: 인증번호 실패 시도 횟수·만료 코드 정리처럼 **실패해도 커밋되어야 하는 기록**은 `VerificationCodeRejectedException` / `VerificationCodeExpiredException`으로 던지고 `noRollbackFor`로 커밋합니다. 롤백되면 5회 입력 제한이 무력화됩니다.
- 새 인증번호 발송 경로는 반드시 `EmailCodeSendQuotaService.tryConsume`(이메일당 24시간 한도)을 거칩니다.

### 인증 / 커뮤니티

- Stateless JWT + HttpOnly refresh cookie 구조를 유지합니다.
- 프론트의 보호 GET은 중앙 `authFetch` refresh 경로를 사용하고, access-token 선행 차단을 넣지 않습니다.
- 401 이후 POST/PATCH/DELETE 같은 변경 요청을 자동 재실행하지 않습니다.
- 비회원 글/댓글은 닉네임 + 작성 비밀번호(BCrypt)로만 수정·삭제합니다.
- 인증 변경 시 익명 401, refresh 경로, 변경 요청 중복 실행 여부를 확인합니다.

### 프록시 / client IP

- `X-Forwarded-For`를 단순히 첫 값/마지막 값으로 신뢰하도록 바꾸지 않습니다.
- `TrustedProxyHeaderFilter`는 Render 환경변수 `TRUSTED_PROXY_SECRET`이 비어 있으면 비활성입니다. 활성화 순서(Vercel 설정 → 승인 후 Vercel 배포 → Render 설정)는 `DEPLOYMENT.md`를 따릅니다. 순서가 바뀌면 모든 Vercel 경유 사용자가 같은 IP로 rate limit됩니다.

### DB / Flyway

- 현재 schema version과 migration 목록은 `Backend/src/main/resources/db/migration/`과 `docs/DB_MIGRATION.md`가 기준입니다.
- 이미 운영에 적용된 migration은 수정하지 않습니다. 스키마 변경은 항상 가장 큰 번호 다음의 새 `V{n}__...` migration으로 추가합니다.
- 운영에서 `ddl-auto=update/create/create-drop`를 사용하지 않습니다(`validate` 고정).
- migration에 실제 사용자 데이터나 비밀값을 넣지 않습니다. 타입 축소·컬럼 삭제·대량 rewrite·데이터 삭제는 사용자 승인과 백업 확인 후에만 진행합니다.
- DB 타입을 바꾸면 entity + migration + service/controller + 회귀 테스트를 함께 바꿉니다.

### 배포 게이트

- **Vercel Production 배포는 사용자의 명시적 승인 없이 만들지 않습니다.** `vercel.json`은 Git 자동 배포가 꺼져 있고, 프론트 작업 묶음이 안정화됐을 때만 수동 배포합니다. 커밋마다 Vercel을 확인하지 않습니다.
- `main` 반영 시 Render가 루트 `Dockerfile`로 frontend build + backend `test bootJar` + 기동을 검증합니다. 배포 후 `live`, Flyway/Hibernate validate 성공, fatal 로그 없음을 확인합니다.
- Render Build Filter가 문서-only 변경을 걸러내지 못할 수 있으므로 문서만 바꾸는 PR은 의미 있는 묶음으로 합칩니다.
- GitHub Actions는 수동 fallback(`workflow_dispatch`)으로만 사용합니다.

### Git

- `feature/*`, `fix/*`, `chore/*`, `perf/*`, `maintenance/*` 브랜치에서 의미 있는 단위로 커밋하고, PR 후 `main`에 **Squash Merge**합니다.
- 버그 수정에는 가능한 한 재현 테스트를 함께 추가합니다.
- 큰 TSX/Java 파일은 전체를 재작성하지 않고 필요한 부분만 patch합니다.
- 비밀값(DB/JWT/SMTP/API key/OAuth), `application-api.yaml`, `.env*`는 절대 커밋하지 않습니다. 실제 값은 Render/Vercel/Aiven 환경변수에만 둡니다.

### 사용자 결정이 필요한 일

자동으로 결정하지 않습니다: Vercel Production 배포, 운영 사용자 데이터 삭제·병합·이메일 변경, 운영 DB 리전 이전, 데이터 손실 가능 migration, 비용이 드는 플랜 변경. 그 외 안전하고 되돌릴 수 있는 코드/테스트/문서 정리는 진행하고 결과를 보고합니다.

## External Services

| 서비스 | 용도 | 비밀값 위치 |
| --- | --- | --- |
| Render | Backend Docker 배포 | Render Environment |
| Vercel | Frontend 배포, `/api` 프록시 | Vercel Project Environment |
| Aiven MySQL | 운영 DB (`defaultdb`) | Render `DB_*` |
| Finnhub / Naver | 시세 / 뉴스 | Render |
| FreeImage | 게시판 이미지 업로드 | Render |
| Google Identity | 로그인 | Render `GOOGLE_CLIENT_ID`, Vercel `VITE_GOOGLE_CLIENT_ID` |
| Gmail SMTP | 인증번호 메일 | Render `MAIL_*` |

환경변수 전체 목록은 `DEPLOYMENT.md`에 있습니다.

## MCP Usage Rules

기본 작업 수단은 **로컬 파일 + Git + 터미널**입니다. MCP는 호출 전에 세 가지를 확인합니다.

1. 현재 작업에 이 정보가 실제로 필요한가?
2. 로컬 파일/Git으로 확인할 수 없는가?
3. 결과에 따라 작업 방향이 달라지는가?

하나라도 아니면 호출하지 않습니다. 등록되어 있다는 이유만으로 Slack, Notion, Aiven, Render, Vercel, GitHub API 등을 탐색하지 않습니다.

- 코드 수정: Local + Git + Terminal만 사용
- 운영 장애 확인: Render 로그 → 필요하면 Aiven → 필요하면 Vercel
- Slack/Notion: 사용자가 직접 요청한 경우에만
- Vercel/Render/Aiven의 쓰기 작업(배포, 설정 변경, 재시작)은 사용자 승인 후에만

## Subagent Usage Rules

Subagent는 별도 context가 실제로 이득일 때만 씁니다(광범위한 조사, 많은 로그 분석, 보안/정합성 리뷰, 중간 결과를 메인 context에 남길 필요가 없는 작업).

단일 파일 수정, 짧은 검색, 간단한 Git 작업, 작은 버그 수정, 이미 메인 context가 충분한 작업에는 쓰지 않습니다. 목표는 agent 수를 늘리는 것이 아니라 메인 context를 깨끗하게 유지하는 것입니다.

Claude Code용 정의는 `.claude/agents/`(ledger-reviewer, auth-community-reviewer, transaction-debugger)와 `.claude/skills/`(verify-money-flow, verify-community-auth, production-check)에 있습니다. 다른 에이전트는 같은 파일을 체크리스트로 참고할 수 있습니다.

## Current State

현재 상태만 유지합니다. 끝난 일은 지우고 Git history에 맡깁니다.

### Completed

- 금액 저장 타입을 `DECIMAL` + `BigDecimal`로 통일하고, overflow를 mutation 전에 거부
- `member.email` UNIQUE 적용, 운영 데이터 1회성 초기화
- 인증번호 5회 입력 제한 롤백 버그 수정, 이메일당 24시간 발송 한도 추가
- 보호 GET의 `authFetch` refresh 경로 통일

### Current

- 진행 중인 코드 작업 없음
- 최근 main 반영분의 Render 배포(Flyway migration 적용, Hibernate validate) 확인 필요

### Next

- `#78` trusted proxy 활성화 — 코드 준비 완료, 위 프록시 규칙의 순서로 진행
- `#76` Vercel Production 배포 후 CSP Report-Only 위반 확인 → 강제 전환 검토
- `#26` Render Health Check Path(`/actuator/health/readiness`), Build Filter 설정
- Dependabot 프론트 의존성 PR은 Vercel 배포 묶음과 함께 처리
- `Docker/docker-compose.yml`, `Data_Worker/`는 현재 구성과 맞지 않는 legacy — 유지/삭제 결정 필요

### Blocked

- `#78`, `#76`: Vercel Production 배포에 사용자 승인 필요
- `#26`: Render Dashboard 수동 설정 필요(자동화 권한 없음)
