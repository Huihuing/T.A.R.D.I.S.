# T.A.R.D.I.S. - Social Investing & Stock Simulation Platform

T.A.R.D.I.S.는 **실시간 시장 데이터 기반 모의투자, 가상 자산 관리, 관심 종목, 뉴스, 커뮤니티와 랭킹**을 결합한 학습·포트폴리오 프로젝트입니다. 실제 자금의 입출금이나 실제 증권 주문은 수행하지 않습니다.

## 주요 기능

- **실시간 시세 기반 모의투자**
  - Finnhub 현재가를 서버에서 조회해 매수·매도 가격을 결정합니다.
  - 클라이언트가 임의 가격을 제출해 체결가를 바꿀 수 없도록 구성했습니다.
- **가상 포트폴리오**
  - 초기 가상 자본금과 모의거래 결과를 기준으로 현금·보유 종목·평단가를 관리합니다.
- **관심 종목 / 뉴스**
  - 관심 종목 저장, Finnhub 및 Naver Open API 기반 시장 뉴스 조회를 지원합니다.
- **커뮤니티**
  - 게시글·댓글, 로그인 사용자 수정/삭제, 비회원 글/댓글을 지원합니다.
  - 비회원 식별 정보는 IP 일부만 마스킹해 표시합니다.
  - 이미지 업로드 키는 서버 환경변수로만 관리합니다.
- **랭킹**
  - 서버가 조회한 현재가를 기준으로 가상 총자산과 수익률을 계산합니다.
- **일일 보상**
  - 출석 및 활동 조건을 서버에서 검증해 정해진 가상 보상을 지급합니다.
  - 현금이 $100 미만일 때 24시간에 한 번 고정 $1,000 긴급지원금을 받을 수 있습니다.
- **실시간 알림**
  - Spring WebSocket/STOMP를 이용한 알림 채널을 제공합니다.

## 보안 구조

- Stateless JWT 인증
- 거래·잔고·경제·북마크·송금 API는 **JWT의 사용자 정보만 신뢰**
- 거래 가격과 긴급지원금은 **서버가 결정**
- 회원 비밀번호·이메일·PIN 및 엔티티 관계의 API 직렬화 차단
- 운영 CORS/WebSocket Origin을 Vercel 도메인으로 제한
- 외부 API 키, JWT 서명키, DB 접속정보는 Git에 저장하지 않고 환경변수/로컬 전용 설정으로 분리
- 기존 레거시 raw-SQL 회원/지갑 API는 공개 버전에서 차단

## 기술 스택

- **Frontend**: React, TypeScript, Vite, Tailwind CSS, Framer Motion, Recharts
- **Backend**: Spring Boot 4.1, Spring Security, Spring Data JPA, Spring WebSocket
- **Database**: MySQL / Aiven Managed MySQL
- **External APIs**: Finnhub, Naver Open API, FreeImage, TradingView
- **Deployment**: Vercel + Render + Aiven

## 라이브 서비스

- Frontend: https://tardis-neon.vercel.app
- Backend: https://t-a-r-d-i-s.onrender.com

## 로컬 개발 및 배포

설정 값과 실행 방법은 [DEPLOYMENT.md](DEPLOYMENT.md)를 참고하세요.

> `Backend/src/main/resources/application-api.yaml`과 `.env*` 파일은 로컬 전용이며 Git에 커밋하지 않습니다.


## 공개판 운영 정책

- **사용자 간 가상자산 송금 지원**
  - Wallet 화면에서 상대방 아이디, 금액, 계좌 PIN을 입력해 다른 사용자에게 가상자산을 송금할 수 있습니다.
  - 송금 시 서버가 JWT 사용자와 PIN을 검증하고, 송신·수신 지갑을 DB write lock으로 보호합니다.
- **임의 입금·출금 비활성화**
  - 공개 버전에서는 사용자가 가상 현금을 임의로 생성하거나 별도 출금하는 기능을 제공하지 않습니다.
  - 잔고는 초기 자금, 출석/활동 보상, 모의투자 결과, 사용자 간 송금으로 변동합니다.
- **비회원 커뮤니티 지원**
  - 로그인하지 않은 사용자도 텍스트 게시글과 댓글을 작성할 수 있습니다.
  - 비회원 작성자는 일부 마스킹된 IP 형태로 표시합니다.
  - 게시글 수정/삭제, 이미지 업로드, 포트폴리오 첨부 등 계정 기반 기능은 로그인 사용자를 대상으로 합니다.
- **한국 시간 기준**
  - 커뮤니티 게시글·댓글과 거래/경제 보상 관련 신규 시간 기록은 `Asia/Seoul` 기준으로 처리합니다.
- **요청 제한**
  - 로그인, 회원가입, 게시글/댓글, 이미지 업로드, 송금, 거래 및 공개 주가/뉴스 요청에 기본적인 rate limit을 적용합니다.
