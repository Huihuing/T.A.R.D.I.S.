# 🚀 T.A.R.D.I.S. 클라우드 배포 및 멀티 개발 환경 가이드

학원, 집 등 **어떤 PC 환경에서도 git clone만 받으면 즉시 개발**할 수 있도록 설계된 환경 세팅 및 클라우드 배포(Aiven + Render + Vercel) 완벽 정리 가이드입니다.

---

## 📌 목차
1. [로컬(집/학원) 멀티 PC 개발 가이드](#1-로컬집학원-멀티-pc-개발-가이드)
2. [Aiven 클라우드 데이터베이스 (MySQL) 세팅](#2-aiven-클라우드-데이터베이스-mysql-세팅)
3. [Render 백엔드 서버 (Spring Boot) 배포](#3-render-백엔드-서버-spring-boot-배포)
4. [Vercel 프론트엔드 (React) 배포](#4-vercel-프론트엔드-react-배포)
5. [자주 겪는 문제 & 트러블슈팅 (FAQ)](#5-자주-겪는-문제--트러블슈팅-faq)

---

## 1. 로컬(집/학원) 멀티 PC 개발 가이드

집과 학원을 오가며 개발할 때 .env 파일 누락으로 오류가 나지 않도록 **스마트 폴백(Fallback)** 코드가 구축되어 있습니다.

### 💡 핵심: 별도의 .env 생성 없이 바로 실행 가능!
- **프론트엔드**: 환경 변수가 없으면 자동으로 http://localhost:8080 (백엔드) 및 http://localhost:3000 (소켓)으로 연결됩니다.
- **백엔드**: 환경 변수가 없으면 자동으로 로컬 MySQL(localhost:3306, root/1234)로 연결됩니다.

### 💻 새 컴퓨터(학원 등)에서 시작할 때 명령어
`ash
# 1. 저장소 클론
git clone https://github.com/Huihuing/T.A.R.D.I.S.
cd T.A.R.D.I.S

# 2. 통합 런처로 한 번에 켜기 (MySQL, 백엔드, 프론트엔드, 소켓서버)
python launcher.py

# 또는 개별 실행:
# [백엔드 실행]
cd Backend
./gradlew bootRun   # Windows: gradlew.bat bootRun

# [프론트엔드 실행]
cd Frontend
npm install
npm run dev
`

---

## 2. Aiven 클라우드 데이터베이스 (MySQL) 세팅

로컬 PC가 꺼져 있어도 24시간 가동되는 영구 무료 클라우드 DB입니다.

1. **[Aiven 콘솔](https://aiven.io/)** 로그인 후 **[Create service]** 클릭
2. ⚠️ **반드시 [MySQL] 선택** (PostgreSQL이 아님에 주의!)
3. 요금제: **Free plan (무료)** 선택 후 서비스 생성 (이름 예: mysql-tardis)
4. 생성 완료 후 **Connection information** 확인:
   - **Host**: mysql-xxxx.aivencloud.com
   - **Port**: 23163 (Aiven이 부여한 5자리 숫자)
   - **User**: vnadmin
   - **Password**: [부여된 비밀번호]
   - **Database**: defaultdb (기본 생성 DB 사용)

---

## 3. Render 백엔드 서버 (Spring Boot) 배포

Java 21 및 Docker 기반으로 Spring Boot 서버를 24시간 호스팅합니다.

1. **[Render 대시보드](https://render.com/)** ➔ **[New +]** ➔ **[Web Service]** 클릭
2. T.A.R.D.I.S. 깃허브 레포지토리 연결
3. **기본 설정**:
   - **Name**: `t-a-r-d-i-s` (실제 배포 주소: `https://t-a-r-d-i-s.onrender.com`)
   - **Environment**: Docker (루트 경로의 Dockerfile을 통해 자동 빌드됨)
   - **Branch**: main
4. **Environment Variables (환경 변수)** 탭에서 3개 등록:
   - DB_URL: jdbc:mysql://[Aiven호스트]:[Aiven포트]/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
   - DB_USERNAME: avnadmin
   - DB_PASSWORD: [Aiven비밀번호]
5. **[Save Changes]** 누르면 자동 배포 진행 ➔ 초록색 Live 확인!

---

## 4. Vercel 프론트엔드 (React) 배포

전 세계에 초고속으로 배포되는 프론트엔드 호스팅입니다.

1. **[Vercel 콘솔](https://vercel.com/)** ➔ **[Add New...]** ➔ **[Project]** 클릭
2. T.A.R.D.I.S. 레포지토리 Import
3. **프로젝트 설정**:
   - **Project Name**: `tardis-neon` (실제 배포 도메인: `https://tardis-neon.vercel.app`)
   - **Root Directory**: Frontend 👈 **(필수! 반드시 Frontend 폴더 지정)**
   - **Framework Preset**: Vite
4. **Environment Variables (환경 변수)** 등록:
   - VITE_API_URL: `https://t-a-r-d-i-s.onrender.com`
   - VITE_WS_URL: `https://t-a-r-d-i-s.onrender.com`
   - ⚠️ **주의**: Type을 Secret이 아닌 **Config** 로 선택 후 저장!
5. **[Deploy]** 클릭 ➔ 배포 완료!

---

## 5. 자주 겪는 문제 & 트러블슈팅 (FAQ)

### Q1. Vercel에서 환경 변수를 수정했는데 웹사이트에 적용이 안 돼요!
- **원인**: React/Vite는 빌드 시점에 환경 변수를 정적 JS 파일에 주입합니다.
- **해결**: Vercel의 **[Deployments]** 탭 ➔ 최신 배포 우측의 ... ➔ **[Redeploy]** 를 반드시 눌러야 새 주소가 반영됩니다.

### Q2. 웹사이트 새로고침(F5) 시 404 Not Found가 떠요!
- **해결**: SPA(Single Page Application) 라우팅을 위해 Frontend/vercel.json 파일에 모든 경로를 /index.html로 돌려주는 rewrite 설정이 이미 추가되어 있습니다.

### Q3. 백엔드 첫 요청이 30~50초 정도 늦게 응답해요!
- **원인**: Render 무료 티어의 특성상 15분 이상 접속이 없으면 서버가 절전 모드(Cold Start)에 들어갑니다.
- **해결**: 첫 요청 시 깨어나는 데 약 40초가 걸리며, 한 번 깨어나면 이후에는 정상 속도로 빠르게 동작합니다.

### Q4. Vercel 환경 변수 등록 시 'Secret' 관련 에러가 떠요!
- **원인**: VITE_ 접두사로 시작하는 브라우저용 공개 환경 변수는 Vercel 정책상 Secret으로 잠글 수 없습니다.
- **해결**: 기존 변수를 **Delete(삭제)** 한 뒤 새로 추가할 때 Type을 **Config** 로 선택해 저장하세요.
