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


* **Watchlist (관심 종목)**
* 마음에 드는 종목을 북마크(⭐)하여 나만의 관심 종목 리스트로 관리 및 모아보기 기능 제공


* **Market News & Custom Thumbnails**
* 해외(Finnhub) 및 국내(네이버 API) 최신 증시 뉴스를 100개까지 확장 수집
* URL 출처 기반의 언론사 맞춤형 로고 및 금융 배경 이미지 매핑 썸네일 시스템 적용


* **Trollbox (실시간 채팅)**
* Node.js Socket.io 기반의 실시간 전체 채팅방을 통해 유저 간 시황 및 의견 공유


* **Community Board & Social Features**
* 투자 전략과 인사이트를 나누는 게시판 (글 작성, 조회, 수정, 삭제) 및 댓글 기능 지원
* **[내 포트폴리오 자랑하기]**: 버튼 하나로 본인의 보유 주식, 수량, 평단가를 마크다운(Markdown) 리스트 형식으로 본문에 자동 첨부
* **[무료 외부 이미지 호스팅 연동]**: 서버 스토리지 부하 방지를 위해 백엔드 프록시(Proxy)를 거쳐 `freeimage.host` API로 이미지를 업로드하고, 본문에 사진이 자동 렌더링되도록 구현

* **Hall of Fame (모의투자 랭킹)**
* 총자산 및 수익률(ROI)을 기준으로 상위 투자자들의 순위를 집계하는 명예의 전당 시스템



---

## 🛠️ Tech Stack

* **Frontend**: React, TypeScript, Tailwind CSS, Framer Motion, Recharts, Lucide React, React Router
* **Backend**: Spring Boot, Spring Security (JWT), Spring Data JPA, Spring WebSocket (STOMP), JdbcTemplate
* **Real-time Socket Server**: Node.js, Express, Socket.io, ws (Finnhub WebSocket Relay)
* **Database**: MySQL / H2 (JPA Entity & Relational Mapping)
* **External APIs**: Finnhub API, Naver Open API (NCP API HUB), TradingView Widget