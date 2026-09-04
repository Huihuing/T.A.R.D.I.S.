# T.A.R.D.I.S. - Social Investing & Stock Simulation Platform

T.A.R.D.I.S.는 실시간 글로벌 주식 시세 스트리밍, 가상 자산 관리, 소셜 커뮤니티, 실시간 채팅 및 랭킹 시스템이 유기적으로 결합된 **종합 소셜 인베스팅(Social Investing) 플랫폼**입니다.

---

## 🚀 Key Features

* **Real-time Stock Market & Interactive Charts**
  * Finnhub API와 WebSocket을 연동하여 주요 글로벌 증시 및 종목의 실시간 시세 스트리밍 제공
  * TradingView 위젯을 통합하여 일봉, 주봉, 월봉 및 한국어 로케일 지원 완벽 구현

* **Virtual Portfolio & Trading Simulator**
  * 회원가입 시 지급되는 초기 자본금($10,000)을 바탕으로 실시간 시세 기반의 가상 매수(BUY)·매도(SELL) 체결
  * Recharts를 활용한 내 자산(현금 및 보유 주식 비중) 도넛 파이 차트 제공
  * 차트 하단에 상세 자산 명세(종목, 수량, 현재가, 실시간 평가손익금 및 수익률) 리스트 표기

* **Virtual Economy & Daily Quests (가상 경제 시스템)**
  * **[일일 출석 체크]**: 매일 첫 로그인/출석 체크 시 $500 시드머니 즉시 지급
  * **[파산 구제 룰렛]**: 총자산이 $100 이하로 떨어졌을 때 재기를 위한 긴급 구제 지원금 룰렛 기회 제공
  * **[일일 퀘스트]**: 첫 거래, 커뮤니티 활동 등 미션 달성에 따른 추가 보상 획득 시스템

* **Watchlist (관심 종목)**
  * 마음에 드는 종목을 북마크(⭐)하여 나만의 관심 종목 리스트로 관리 및 모아보기 기능 제공

* **Market News & Custom Thumbnails**
  * 해외(Finnhub) 및 국내(네이버 API) 최신 증시 뉴스를 100개까지 확장 수집
  * URL 출처 기반의 언론사 맞춤형 로고 및 금융 배경 이미지 매핑 썸네일 시스템 적용

* **Trollbox (실시간 채팅)**
  * Node.js Socket.io 기반의 실시간 전체 채팅방을 통해 유저 간 시황 및 의견 공유

* **Community Board & Social Features**
  * 투자 전략과 인사이트를 나누는 게시판 (글 작성, 조회, 수정, 삭제) 및 댓글 기능 지원
  * **[비회원(Guest) 게시판 이용 지원]**: 로그인하지 않은 사용자도 게시물 및 댓글 작성이 가능하며, 작성자 식별을 위해 IP 일부를 마스킹하여(예: `ㅇㅇ(39.7.*.*)`) 표시
  * **[내 포트폴리오 자랑하기]**: 버튼 하나로 본인의 보유 주식, 수량, 평단가를 마크다운(Markdown) 리스트 형식으로 본문에 자동 첨부
  * **[무료 외부 이미지 호스팅 연동]**: 서버 스토리지 부하 방지를 위해 백엔드 프록시(Proxy)를 거쳐 `freeimage.host` API로 이미지를 업로드하고, 본문에 사진이 자동 렌더링되도록 구현

* **Hall of Fame (실시간 모의투자 랭킹)**
  * **[Finnhub 실시간 시세 반영]**: 보유 종목의 가치를 매수 평단가가 아닌 실제 시장 가격으로 실시간 산출하여 실질적인 총자산 및 ROI(수익률) 랭킹 집계
  * **[60초 인메모리 스마트 캐싱]**: 외부 시세 API의 호출 한도(Rate Limit) 초과를 방지하고 응답 속도를 극대화하기 위해 종목별 캐싱 및 장애 시 평단가 자동 폴백 적용

* **Multi-Environment & 통합 런처 (`launcher.py`)**
  * 집, 학원 등 환경 변수가 없는 환경에서도 기본값으로 즉시 실행되는 스마트 폴백(Fallback) 구조 내장
  * 비동기 스레드 기반의 GUI 런처를 통해 MySQL, 백엔드, 프론트엔드, 소켓서버를 원클릭으로 일괄/개별 제어

* **Security & API Architecture**
  * **[Stateless JWT 인증 방식 복구]**: 세션을 사용하지 않는(Stateless) 토큰 기반 보안 정책을 활성화하고, 잔고/주문/가상경제 등 주요 거래 API에 대해 `JwtAuthenticationFilter` 기반의 강력한 접근 제어 적용
  * **[클라우드 배포(CORS) 최적화]**: Vercel(프론트)과 Render(백엔드) 간의 원활한 통신을 위해 모든 출처(Origin)에 대해 CORS를 허용하고, 브라우저의 OPTIONS 프리플라이트 요청을 승인하여 403 Forbidden 이슈 완벽 차단

---

## 🛠️ Tech Stack

* **Frontend**: React, TypeScript, Vite, Tailwind CSS, Framer Motion, Recharts, Lucide React, React Router
* **Backend**: Spring Boot 4.1, Spring Security (Stateless JWT), Spring Data JPA, Spring WebSocket (STOMP), RestTemplate
* **Real-time Socket Server**: Node.js, Express, Socket.io, ws (Finnhub WebSocket Relay)
* **Database**: MySQL 8.0 (Local) / Aiven Managed MySQL (Cloud)
* **External APIs**: Finnhub API (실시간 시세 및 REST), Naver Open API (NCP API HUB 증시 뉴스), FreeImage API (게시판 이미지 호스팅), TradingView Widget
* **Cloud & DevOps**: Vercel (Frontend), Render (Backend & Docker), Aiven (Cloud Database)

---

## 📖 Deployment & Setup Guide

* **라이브 프론트엔드**: [https://tardis-neon.vercel.app](https://tardis-neon.vercel.app)
* **라이브 백엔드**: [https://t-a-r-d-i-s.onrender.com](https://t-a-r-d-i-s.onrender.com)
* 학원이나 집 등 다른 PC에서의 실행 방법 및 클라우드 배포 상세 설정은 [DEPLOYMENT.md](DEPLOYMENT.md) 문서를 참고하세요.