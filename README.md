# T.A.R.D.I.S. - Social Investing & Stock Simulation Platform

T.A.R.D.I.S.는 **실시간 시장 데이터 기반 모의투자, 가상 자산 관리, 관심 종목, 뉴스, 커뮤니티와 랭킹**을 결합한 학습·포트폴리오 프로젝트입니다. 실제 자금의 입출금이나 실제 증권 주문은 수행하지 않습니다.

## 주요 기능

- **실시간 시세 기반 모의투자**
  - Finnhub 현재가를 서버에서 조회해 매수·매도 가격을 결정합니다.
  - 클라이언트가 임의 가격을 제출해 체결가를 바꿀 수 없도록 구성했습니다.
- **가상 포트폴리오 / 통합 거래원장**
  - 현금·보유 종목·평단가와 함께 매수·매도·송금·보상 등 가상자산 변동 이력을 기록합니다.
- **사용자 간 가상자산 송금**
  - 송금 PIN 검증과 DB write lock으로 송신·수신 지갑을 보호합니다.
- **인증**
  - 일반 회원가입은 이메일 인증이 필요합니다.
  - Google 로그인은 Google 검증 이메일을 사용하며 별도 이메일 인증을 요구하지 않습니다.
  - 신규 SNS 계정은 첫 로그인 후 송금용 PIN을 설정합니다.
  - PIN 분실 시 계정 이메일의 6자리 보안 인증번호로 재설정할 수 있습니다.
  - Google 전용 계정은 이메일 인증 후 일반 비밀번호 로그인을 추가할 수 있습니다.
  - 비밀번호·PIN·Google 연결 변경은 보안 알림으로 기록합니다.
- **관심 종목 / 뉴스**
  - 관심 종목 저장, Finnhub 및 Naver Open API 기반 시장 뉴스 조회를 지원합니다.
- **커뮤니티**
  - 회원 게시글·댓글과 비회원 게시글·댓글을 지원합니다.
  - 비회원은 닉네임과 작성 비밀번호를 필수 입력하고, 같은 비밀번호로 자신의 글/댓글을 수정·삭제합니다.
- **랭킹 / 일일 보상 / 실시간 알림**
  - 서버 기준 총자산/수익률 랭킹, 활동 보상, WebSocket 알림을 제공합니다.

## 보안 구조

- Stateless JWT 인증
- JWT 발급 → Bearer 전송 → 서버 필터 인증 연결 테스트
- 거래·잔고·경제·북마크·송금 API는 JWT 사용자 정보만 신뢰
- 비밀번호·PIN은 BCrypt 해시 저장
- 거래/송금/보상 변경 경로 DB write lock 적용
- 로그인/회원가입/게시판/거래 등에 rate limit 적용
- 외부 API 키, JWT 서명키, DB/SMTP 비밀번호는 Git이 아닌 환경변수로 관리
- Google 로그인 ID token은 백엔드에서 Google 검증 결과와 Client ID(audience)를 확인

## 기술 스택

- **Frontend**: React, TypeScript, Vite, Tailwind CSS, Framer Motion, Recharts
- **Backend**: Spring Boot 4.1, Spring Security, Spring Data JPA, Spring WebSocket
- **Database**: MySQL / Aiven Managed MySQL
- **External APIs**: Finnhub, Naver Open API, FreeImage, Google Identity Services, TradingView
- **Deployment**: Vercel + Render + Aiven

## CI / 배포 검증

- **Vercel**: Frontend 프로덕션 빌드/배포 검증
- **Render**: Spring Boot Docker 빌드/기동 검증
- **GitHub Actions**: 자동 실행하지 않고 필요할 때만 수동 fallback으로 사용

## 라이브 서비스

- Frontend: https://tardis-neon.vercel.app
- Backend: https://t-a-r-d-i-s.onrender.com

## 로컬 개발 및 배포

설정 값과 실행 방법은 [DEPLOYMENT.md](DEPLOYMENT.md)를 참고하세요.

> `Backend/src/main/resources/application-api.yaml`과 `.env*` 파일은 로컬 전용이며 Git에 커밋하지 않습니다.
