# T.A.R.D.I.S. 로컬 개발 및 배포 가이드

이 문서는 공개 저장소에서 **비밀값을 커밋하지 않고** 로컬·Render·Vercel 환경을 구성하는 방법을 설명합니다.

## 1. 로컬 개발

저장소를 클론한 뒤 의존성을 설치합니다.

```powershell
git clone https://github.com/Huihuing/T.A.R.D.I.S..git
cd T.A.R.D.I.S.

cd Frontend
npm install
npm run dev
```

백엔드는 Java 21 기준입니다.

```powershell
cd Backend
.\gradlew.bat bootRun
```

### 백엔드 로컬 비밀 설정

`Backend/src/main/resources/application-api.example.yaml`을 복사해
`Backend/src/main/resources/application-api.yaml`로 만들고 실제 값을 입력합니다.

`application-api.yaml`은 Git에서 무시됩니다.

## 2. Render 백엔드

Render Web Service는 저장소 루트의 `Dockerfile`을 사용합니다.

필수/기능별 환경변수:

| 변수 | 용도 |
| --- | --- |
| `DB_URL` | Aiven MySQL JDBC URL |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `FINNHUB_API_KEY` | Finnhub REST API |
| `NAVER_CLIENT_ID` | Naver Open API Client ID |
| `NAVER_CLIENT_SECRET` | Naver Open API Client Secret |
| `FREEIMAGE_API_KEY` | 게시판 이미지 업로드 |
| `JWT_SECRET` | JWT HMAC 서명키, 최소 32바이트 |
| `FRONTEND_URL` | 운영 프론트 Origin |
| `GOOGLE_CLIENT_ID` | Google Identity Services Web Client ID |
| `MAIL_USERNAME` | 일반 회원가입 인증메일 발신 계정 |
| `MAIL_PASSWORD` | SMTP 비밀번호. Gmail 사용 시 앱 비밀번호 권장 |

선택 환경변수:

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP 서버 |
| `MAIL_PORT` | `587` | SMTP 포트 |
| `JPA_SHOW_SQL` | `false` | SQL 로그 출력 여부 |
| `JPA_DDL_AUTO` | `update` | Hibernate 스키마 정책. 마이그레이션 도입 후 `validate` 전환 권장 |
| `JWT_EXPIRATION_MS` | `900000` | access JWT 유효시간(ms), 기본 15분. refresh cookie로 로그인 유지 |
| `DB_POOL_MAX_SIZE` | `5` | Hikari 최대 DB 연결 수. Aiven free 1GB 단일 노드 기준 |
| `DB_POOL_MIN_IDLE` | `1` | 유휴 상태에서 유지할 최소 연결 수 |
| `DB_CONNECTION_TIMEOUT_MS` | `10000` | DB 연결 획득 대기 시간 |
| `DB_INITIALIZATION_FAIL_TIMEOUT_MS` | `60000` | Hikari 초기 DB 연결 재시도 허용 시간(ms) |
| `DB_VALIDATION_TIMEOUT_MS` | `5000` | 연결 유효성 검사 제한 시간 |
| `DB_IDLE_TIMEOUT_MS` | `300000` | 최소 연결 수를 초과한 유휴 연결 정리 시간 |
| `DB_MAX_LIFETIME_MS` | `1500000` | 풀 연결 최대 수명 |
| `DB_KEEPALIVE_TIME_MS` | `120000` | 장시간 유휴 연결 keepalive 주기 |

비밀값은 Render Environment에만 저장하고 GitHub에는 입력하지 않습니다.

### Render health check

백엔드는 Spring Boot Actuator의 `/actuator/health`만 공개합니다.
응답은 상세 내부정보를 노출하지 않고 DB 연결 상태를 포함한 전체 health만 제공합니다.
Render Dashboard의 Health Check Path는 `/actuator/health`로 설정하는 것을 권장합니다.

## 3. Vercel 프론트엔드

Root Directory를 `Frontend`로 지정합니다.

```text
VITE_WS_URL=https://t-a-r-d-i-s.onrender.com
VITE_GOOGLE_CLIENT_ID=<Google OAuth Web Client ID>
```

`VITE_*` 값은 브라우저 번들에 포함됩니다. **비밀번호, API secret, JWT secret은 절대 VITE 변수에 넣지 않습니다.**

Google OAuth Web Client ID는 공개 식별자이므로 프론트와 백엔드가 같은 값을 사용해도 됩니다. Google Cloud Console의 해당 Web Client에는 운영 Origin으로 `https://tardis-neon.vercel.app`을 등록합니다.

## 4. 인증 정책

- 일반 회원가입: 이메일 인증번호 확인 후 가입 가능
- Google 로그인: Google이 검증한 이메일(`email_verified=true`)을 사용하므로 별도 이메일 인증 생략
- 신규 Google 계정: 첫 로그인 후 송금용 숫자 4자리 PIN을 1회 설정
- 기존 일반 계정과 Google 이메일이 같으면 해당 계정에 Google 로그인을 연결
- 비밀번호/PIN/SMTP 비밀번호/Google ID token을 Git에 저장하지 않음

## 5. Aiven MySQL

Render의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`에 Aiven Connection information을 사용합니다.

예시 형식:

```text
jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
```

실제 Host, Port, 사용자명, 비밀번호는 문서나 Git에 커밋하지 않습니다.

현재 운영 Aiven MySQL은 실수로 서비스가 삭제되거나 전원 종료되는 것을 막기 위해
termination protection을 활성화합니다. Render의 outbound IP가 고정되지 않은 환경에서는
Aiven IP allowlist를 임의로 좁히지 않습니다. 고정 egress를 도입한 뒤에만 제한 범위를 축소합니다.

## 6. 공개 저장소 체크리스트

- `application-api.yaml`, `.env*`, DB 비밀번호가 Git에 없음
- 외부 API 키가 코드와 과거 Git history에 없음
- Render/Vercel 최신 배포가 정상
- 일반 회원가입 인증메일 실제 발송 확인
- Google 로그인 Origin/Client ID 설정 확인
- 보호 API가 익명 요청에 401을 반환하는지 smoke test 확인


## 7. Google 로그인 / 이메일 인증

Google 로그인은 백엔드의 `GOOGLE_CLIENT_ID`와 프론트 Vercel의
`VITE_GOOGLE_CLIENT_ID`에 **같은 Web Client ID**를 설정합니다.

일반 회원가입과 비밀번호 재설정 메일 발송에는 Render에
`MAIL_USERNAME`, `MAIL_PASSWORD`를 설정합니다. 실제 비밀번호나 앱 비밀번호는
GitHub에 커밋하지 않습니다.

Google로 새로 생성된 SNS 전용 계정은 별도의 이메일 인증이나 로컬 비밀번호
재설정을 요구하지 않습니다.

### 계정 보안 복구

로그인된 사용자는 계정 설정에서 이메일 6자리 보안 인증번호를 요청할 수 있습니다.

- PIN 분실: 인증번호 확인 후 새 4자리 PIN 설정
- Google 전용 계정: 인증번호 확인 후 8~64자 일반 비밀번호 로그인 추가
- 인증번호 유효시간: 10분
- 잘못된 인증번호 입력 제한: 5회
- 재발송 제한: 1분
- 비밀번호 재설정용 코드와 계정 보안용 코드는 목적이 분리되어 서로 교차 사용할 수 없음
- 비밀번호/PIN/Google 연결 변경은 `SECURITY` 알림으로 기록

## 8. 관리자 페이지

Render의 `ADMIN_USERNAMES`에 애플리케이션 아이디를 쉼표로 구분해 지정합니다.
예: `admin1,admin2`

관리자 페이지는 운영 통계 조회 용도이며 사용자 잔고를 임의로 수정하는 기능은
제공하지 않습니다.


## 9. CI / 배포 검증 정책

GitHub Actions 사용량을 줄이기 위해 일반 push/PR에서는 Actions CI를 자동 실행하지 않습니다.

기본 검증 경로:

1. GitHub `main`에 커밋
2. Vercel이 `Frontend`를 빌드하고 Production 배포
3. Render가 루트 `Dockerfile`에서 `./gradlew test bootJar --no-daemon`을 실행
4. 백엔드 테스트가 모두 통과한 경우에만 Render 이미지 빌드·기동 진행
5. Vercel이 `READY`, Render가 `live`인지 확인
6. 문제가 있으면 각 플랫폼의 build/runtime log를 기준으로 수정

GitHub Actions의 `CI (Manual Fallback)`과 `Deployment Smoke Test`는
필요할 때만 `workflow_dispatch`로 수동 실행합니다.

이 방식은 GitHub Actions가 일시적으로 제한되어도 배포 검증 흐름을 유지하기 위한 운영 정책입니다.


### Vercel 수동 배포 정책

`Frontend/vercel.json`은 `git.deploymentEnabled=false`로 설정합니다.
따라서 GitHub push/PR은 Vercel deployment를 자동 생성하지 않습니다.

운영 원칙:

- GitHub 커밋 횟수에는 제한을 두지 않음
- Render는 커밋마다 frontend build + backend test를 계속 검증
- Vercel은 프론트 기능/UX 작업 묶음이 완료됐을 때만 수동 Production deployment 생성
- 프론트 묶음 도중의 중간 커밋은 Vercel build quota를 소비하지 않음
- 수동 deployment를 만든 뒤 `READY` 상태와 실제 페이지를 확인하고 다음 프론트 묶음으로 이동

이 정책의 목적은 Vercel `build-rate-limit`을 피하면서 GitHub 커밋을 개발 기록으로 자유롭게 유지하는 것입니다.


### CSP Report-Only 운영 게이트

운영 프론트의 CSP는 즉시 차단 모드로 전환하지 않고 먼저 `Content-Security-Policy-Report-Only`로 관찰합니다.

현재 정책에서 의도적으로 허용하는 외부 출처:

- Google Identity Services: `https://accounts.google.com`
- TradingView 차트 iframe: `https://s.tradingview.com`
- Render API/WebSocket: `https://t-a-r-d-i-s.onrender.com`, `wss://t-a-r-d-i-s.onrender.com`
- 뉴스 썸네일은 여러 외부 제공처를 사용하므로 `img-src https:`를 유지

CSP 위반 보고는 same-origin `/api/security/csp-report`로 보내고 Vercel rewrite가 Render API로 전달합니다.
백엔드는 legacy `csp-report` payload와 최신 Reporting API 형태를 모두 처리하며 URI의 query/fragment는 로그에 남기지 않습니다.

강제 정책으로 전환하기 전 확인 순서:

1. 프론트 작업 묶음을 수동 Production deployment
2. 로그인/Google 로그인, Dashboard TradingView, 뉴스 이미지, WebSocket 알림, 게시판 주요 화면 확인
3. Render 로그에서 CSP 위반을 확인하고 필요한 출처만 최소 추가
4. 정상 트래픽에서 의미 있는 위반이 없을 때 `Content-Security-Policy-Report-Only`를 `Content-Security-Policy`로 전환

Vite가 생성한 `/assets/*` 해시 파일은 파일명이 변경될 때 URL도 바뀌므로
`Cache-Control: public, max-age=31536000, immutable`을 적용합니다.
HTML과 비해시 파일은 Vercel의 재검증 정책을 유지합니다.
