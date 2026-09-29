# AGENTS.md — T.A.R.D.I.S. 작업 가이드

이 파일은 새 ChatGPT/Codex 세션이나 다른 작업 에이전트가 저장소 상태를 빠르게 파악하고, 기존 운영 원칙을 깨지 않도록 하기 위한 저장소 루트 가이드입니다.

## 1. 프로젝트 성격

T.A.R.D.I.S.는 **실시간 시장 데이터 기반 모의투자·가상자산·커뮤니티·랭킹 학습/포트폴리오 프로젝트**입니다.

- 실제 자금 입출금이나 실제 증권 주문을 수행하지 않습니다.
- Frontend: React + TypeScript + Vite + Tailwind CSS
- Backend: Spring Boot 4.1 + Spring Security + Spring Data JPA
- Database: Aiven Managed MySQL 8.4
- Deployment: Vercel + Render + Aiven
- Source of truth: GitHub `Huihuing/T.A.R.D.I.S.`

## 2. 새 작업을 시작할 때 읽을 문서

작업 전 최소한 아래 순서로 확인합니다.

1. `README.md` — 프로젝트 기능/구성 요약
2. `AGENTS.md` — 작업 규칙과 운영 게이트
3. `docs/DB_MIGRATION.md` — DB/Flyway의 현재 기준
4. `DEPLOYMENT.md` — 로컬/Render/Vercel 배포 절차
5. `docs/INFRASTRUCTURE.md` — 실제 인프라 특성과 후속 운영 작업
6. `SECURITY.md` — 보안 정책

문서가 서로 충돌하면 **현재 코드와 실제 플랫폼 상태를 먼저 확인**하고, DB 스키마 정책은 `docs/DB_MIGRATION.md`를 우선합니다. 오래된 문서를 발견하면 기능 변경과 함께 정리합니다.

## 3. 현재 아키텍처

```text
Browser
  -> Vercel Frontend
      -> same-origin /api/* rewrite
          -> Render Spring Boot Backend
              -> Aiven MySQL
              -> Finnhub / Naver / FreeImage / Google APIs
```

현재 운영 주소:

- Frontend: `https://tardis-neon.vercel.app`
- Backend: `https://t-a-r-d-i-s.onrender.com`

현재 확인된 인프라:

- Render: Oregon, Docker, free instance
- Aiven MySQL: DigitalOcean Bangalore, MySQL 8.4.x, TLS required
- Render와 Aiven은 서로 다른 리전이므로 DB round-trip 지연 가능성이 있습니다.
- Render Health Check Path는 현재 별도 설정되지 않았으며 `/actuator/health/readiness`가 권장값입니다.

## 4. 절대 지켜야 할 배포 규칙

### Vercel

**사용자의 명시적 승인 없이 Production deployment를 생성하지 않습니다.**

`Frontend/vercel.json`은 Git 자동 배포를 끄는 방향으로 운영합니다. GitHub에는 개발 커밋을 자유롭게 남기되, 프론트 기능/UX 묶음이 완성된 뒤에만 수동 Vercel Production 배포를 진행합니다.

즉:

```text
GitHub commit/PR
  -> Render 검증은 계속
  -> Vercel Production은 사용자 승인 전까지 보류
```

### Render

`main` 반영 시 Render가 루트 `Dockerfile` 기준으로 빌드/기동 검증을 수행합니다.

배포 후 확인할 것:

- build 성공
- backend tests + `bootJar` 성공
- Flyway validate/migrate 성공
- Hibernate schema validate 성공
- 최종 deployment `live`
- runtime log에 DB/JPA/Flyway fatal error 없음

현재 Build Filter가 문서-only 변경을 제외하지 못할 수 있으므로, 문서만 바꾸는 작은 PR도 불필요한 Render 배포를 만들 수 있습니다. 관련 변경은 가능하면 의미 있는 작업 묶음으로 합칩니다.

### GitHub Actions

일반 push/PR에서 자동 CI를 전제로 하지 않습니다. Actions는 사용량 절약을 위해 **수동 fallback** 용도로만 사용합니다.

## 5. Git 작업 원칙

- 가능하면 `feature/*`, `fix/*`, `chore/*`, `perf/*`, `maintenance/*` 브랜치에서 작업합니다.
- 기능적으로 의미 있는 단위로 커밋합니다.
- 수십~수백 개의 미세 커밋보다 검토 가능한 의미 단위를 우선합니다.
- 완료된 묶음은 PR로 확인한 뒤 `main`에 **Squash Merge**하는 흐름을 선호합니다.
- 큰 TSX/Java 파일에서 몇 줄만 고칠 때 전체 파일을 수작업으로 재작성하지 않습니다. patch 가능한 도구가 없으면 안전한 대안을 먼저 찾습니다.

## 6. DB / Flyway 현재 기준

현재 운영 DB 기준:

```text
Flyway: enabled
Schema version: 10
baseline-on-migrate: false
Hibernate ddl-auto: validate
Database: Aiven MySQL 8.4.x / defaultdb
```

핵심 규칙:

- 이미 운영 적용된 `V1`~`V10` migration은 수정하지 않습니다.
- 다음 스키마 변경은 반드시 `V11__...` 이상의 새 migration으로 추가합니다.
- 운영에서 `ddl-auto=update/create/create-drop`를 사용하지 않습니다.
- migration에 실제 사용자 데이터나 비밀값을 하드코딩하지 않습니다.
- 타입 축소, 컬럼 삭제, 대량 rewrite는 백업/lock 영향 검토 없이 수행하지 않습니다.

현재 주요 정밀도 정책:

- Wallet / Ledger / PortfolioSnapshot: `DECIMAL(19,2)` + `BigDecimal`
- LimitOrder / PriceAlert 사용자 입력 가격: `DECIMAL(19,2)`
- Portfolio average price / TradeHistory / Bookmark 시장 가격: `DECIMAL(19,6)`
- 현금 센트 반올림은 `MoneyMath.roundCents`의 HALF_UP 규칙을 사용합니다.

### V8 ~ V10 적용 이력

- `V8`: case-insensitive 중복 이메일을 비파괴적으로 격리한 뒤 `member.email` UNIQUE(`uk_member_email`) 적용. issue `#104` 종료.
- `V9`: 사용자 명시 승인(2026-09-28)에 따른 1회성 운영 데이터 전체 초기화. 스키마와 Flyway history는 유지.
- `V9`는 이미 운영에 적용된 1회성 migration입니다. 같은 성격의 데이터 삭제 migration을 다시 추가하지 않습니다. 데이터 삭제/초기화는 §13에 따라 매번 별도 사용자 승인이 필요합니다.
- `V10`: 이메일 인증번호 발송 한도 테이블 `email_code_send_quota` 추가(추가 전용).

## 7. 인증 / 프론트 규칙

- Stateless JWT + HttpOnly refresh cookie 구조를 유지합니다.
- 보호 API는 서버가 JWT 사용자 정보만 신뢰합니다.
- 프론트의 보호 GET은 중앙 `authFetch` refresh 경로를 거치게 합니다.
- 401 후 POST/PATCH/DELETE 같은 변경 요청을 자동 재실행하지 않습니다.
- 보호 GET 앞의 access-token 선행 차단은 제거되었습니다(issue `#79` 종료). 새 보호 GET을 추가할 때도 선행 차단을 다시 넣지 않습니다.
- 20~50KB TSX 파일 전체를 위험하게 다시 쓰지 않습니다.
- `@Transactional` 경로에서 예외를 catch해 오류 응답을 반환할 때는 `TransactionRollbackSupport.markRollbackOnlyIfActive()`로 부분 변경을 롤백합니다.
- 단, 이메일 인증번호 실패 시도 횟수/만료 코드 정리처럼 **실패해도 커밋되어야 하는 기록**은 `VerificationCodeRejectedException` / `VerificationCodeExpiredException`으로 던지고 `noRollbackFor`로 커밋합니다. 이 기록이 롤백되면 5회 입력 제한이 무력화됩니다.
- 인증번호 재발송은 attempts를 0으로 되돌리므로, 새 인증번호 발송 경로를 추가할 때는 반드시 `EmailCodeSendQuotaService.tryConsume`(이메일당 24시간 한도)을 거칩니다.

## 8. 프록시 / 클라이언트 IP 규칙

`X-Forwarded-For`를 단순히 첫 값/마지막 값으로 신뢰하도록 바꾸지 않습니다.

현재 준비된 구조(issue `#78`, 아직 운영 비활성):

- `Frontend/vercel.json`의 `/api/*` route가 Vercel 환경변수 `TRUSTED_PROXY_SECRET`을 `X-Tardis-Proxy-Secret` 요청 헤더로 주입합니다.
- 백엔드 `TrustedProxyHeaderFilter`는 Render 환경변수 `TRUSTED_PROXY_SECRET`이 **비어 있으면 아무 일도 하지 않습니다.**
- 값이 설정되면 secret이 일치하는 요청만 `X-Forwarded-For` 첫 값을 신뢰하고, 그 외 직접 요청은 `CF-Connecting-IP`(Render edge) 또는 socket 주소를 사용합니다.

활성화 순서(순서가 바뀌면 모든 Vercel 경유 사용자가 같은 IP로 rate limit됩니다):

1. Vercel Project Environment에 `TRUSTED_PROXY_SECRET` 설정
2. 사용자 승인 후 Vercel Production 배포(새 `vercel.json` 반영)
3. Render에 같은 `TRUSTED_PROXY_SECRET` 설정 후 재배포
4. 운영에서 client IP 해석과 rate limit 동작 확인

Render edge의 `CF-Connecting-IP`가 외부 요청에서 덮어써지는지는 아직 운영 검증 전입니다. 그 외 Cloudflare 전용 가정은 추가하지 않습니다.

## 9. 비밀정보 / 보안

절대 Git에 커밋하지 않을 것:

- DB credentials
- JWT secret
- SMTP password
- 외부 API secret/key
- OAuth token/secret
- `Backend/src/main/resources/application-api.yaml`
- `.env*`

실제 비밀값은 Render/Vercel/Aiven 환경변수 또는 각 플랫폼 secret 저장소에서만 관리합니다.

## 10. 로컬 검증 명령

Frontend:

```powershell
cd Frontend
npm install
npm run build
```

Backend:

```powershell
cd Backend
.\gradlew.bat test bootJar --no-daemon
```

백엔드 로컬 실행:

```powershell
cd Backend
.\gradlew.bat bootRun
```

Java 기준은 21입니다.

## 11. 변경 시 테스트 원칙

- 버그 수정에는 가능한 한 재현 테스트를 먼저 또는 함께 추가합니다.
- 금액/가격 경계는 `NaN`, `Infinity`, 음수, 반올림 경계, DB precision 초과를 확인합니다.
- DB persistence 타입을 바꾸면 entity + migration + service/controller + 회귀 테스트를 함께 확인합니다.
- 인증 변경은 익명 401, refresh 경로, 변경 요청 중복 실행 여부를 확인합니다.
- repository/query 변경은 Render free 환경의 LAZY repository bootstrap 특성을 고려합니다.

## 12. 현재 운영상 남은 중요한 작업

새 세션에서 무작정 재설계하지 말고 아래 추적 이슈를 먼저 확인합니다.

- `#78` — Vercel -> Render trusted proxy / client IP 경계. 코드 준비 완료, 위 §8 순서로 활성화 필요
- `#76` — Vercel Production rollout / CSP 후속. **사용자 승인 필요**
- `#26` — Render Health Check Path / Build Filter 등 Dashboard 수동 설정

이슈 상태는 시간이 지나면 바뀔 수 있으므로 실제 GitHub 상태를 다시 확인합니다.

## 13. 사용자 의사결정이 필요한 경우

다음은 자동으로 결정하지 않습니다.

- Vercel Production 배포
- 운영 사용자 데이터 삭제/병합/이메일 변경
- 운영 DB 리전 이전
- 데이터 손실 가능성이 있는 migration
- 비용이 발생하는 인프라 플랜 변경

그 외 안전하고 되돌릴 수 있는 코드/테스트/문서 정리는 가능한 범위에서 계속 진행하고, 막힌 지점과 검증 결과를 명확히 보고합니다.
