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

로컬 DB 비밀번호가 있다면 아래 중 하나를 사용하세요.

- 환경변수 `DB_PASSWORD`
- 로컬 전용 `application-api.yaml`의 `spring.datasource.password`

## 2. Render 백엔드

Render Web Service는 저장소 루트의 `Dockerfile`을 사용합니다.

필수 환경변수:

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
| `FRONTEND_URL` | 운영 프론트 Origin, 현재 `https://tardis-neon.vercel.app` |

선택 환경변수:

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `JPA_SHOW_SQL` | `false` | SQL 로그 출력 여부 |

비밀값은 Render Environment에만 저장하고 GitHub에는 입력하지 않습니다.

## 3. Vercel 프론트엔드

Root Directory를 `Frontend`로 지정하고 Vite 프로젝트로 배포합니다.

권장 환경변수:

```text
VITE_API_URL=https://t-a-r-d-i-s.onrender.com
VITE_WS_URL=https://t-a-r-d-i-s.onrender.com
```

`VITE_*` 값은 브라우저 번들에 포함될 수 있으므로 **비밀키를 절대 넣지 않습니다.**

## 4. Aiven MySQL

Render의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`에 Aiven Connection information을 사용합니다.

예시 형식:

```text
jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
```

실제 Host, Port, 사용자명, 비밀번호는 문서나 Git에 커밋하지 않습니다.

## 5. 공개 저장소 체크리스트

공개 전 다음을 확인합니다.

- `application-api.yaml`, `.env*`, DB 비밀번호가 Git에 없음
- 외부 API 키가 코드와 과거 Git history에 없음
- 커밋 작성자 이메일이 GitHub noreply로 정리됨
- Render와 Vercel 최신 배포가 정상
- Naver/Finnhub/FreeImage 등 과거에 커밋된 적이 있는 자격정보는 재발급 후 Render/로컬 설정을 갱신
