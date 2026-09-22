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
| `JWT_EXPIRATION_MS` | `86400000` | JWT 유효시간(ms), 기본 24시간 |

비밀값은 Render Environment에만 저장하고 GitHub에는 입력하지 않습니다.

## 3. Vercel 프론트엔드

Root Directory를 `Frontend`로 지정합니다.

```text
VITE_API_URL=https://t-a-r-d-i-s.onrender.com
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
3. Render가 루트 `Dockerfile`로 백엔드를 빌드·기동
4. Vercel이 `READY`, Render가 `live`인지 확인
5. 문제가 있으면 각 플랫폼의 build/runtime log를 기준으로 수정

GitHub Actions의 `CI (Manual Fallback)`과 `Deployment Smoke Test`는
필요할 때만 `workflow_dispatch`로 수동 실행합니다.

이 방식은 GitHub Actions가 일시적으로 제한되어도 배포 검증 흐름을 유지하기 위한 운영 정책입니다.
