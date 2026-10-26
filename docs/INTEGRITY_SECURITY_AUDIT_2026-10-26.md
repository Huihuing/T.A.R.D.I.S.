# 거래·보안·운영 통합 감사 — 2026-10-26

## 기준과 운영 상태

- GitHub main: `123aea3e06baa59ab75a262621f14ce508779011`.
- 최초 로컬 HEAD `49f6bae`는 오래된 체크아웃이었다. 사용자 wrapper 변경을 보관한 뒤 최신 main으로 fast-forward하고 수정했다. 해당 wrapper 내용은 최신 main과 동일하며 이번 기능 diff에 포함하지 않는다.
- 작업 브랜치: `fix/trading-security-stability`. 변경 파일과 최종 커밋은 이 브랜치의 Git diff/log 및 PR에서 확인한다.
- Vercel Production: `123aea3e06baa59ab75a262621f14ce508779011`, `dpl_4oFXFFmKWKmd2WwoCFGCEWML4KMJ`, READY. Production alias를 직접 조회했다.
- Render LIVE: 같은 SHA, `dep-db62pkvlot8c738loa9g`. 현재 cold-start 로그에서 Flyway schema **10**, Hibernate/JPA 초기화와 기동 성공을 확인했다.
- 새 **V11은 운영 미적용**이며 이번 작업에서 Production 배포·운영 데이터 변경·환경변수 변경·플랜 변경을 하지 않았다.
- Render readiness: `200 {"status":"UP"}`. 전체 health: `503 DOWN`.
- 전체 health 실패 원인: 현재 Render `MailHealthIndicator` 로그의 `AuthenticationFailedException: failed to connect, no password specified?`. DB 장애로 판정하지 않는다. 유효한 SMTP 비밀값 설정과 실제 메일 발송 검증이 남아 있다.

## 작업별 결과

| ID | 문제 | 심각도 | 수정 여부 | 테스트 결과 | 잔여 위험 |
|---|---|---|---|---|---|
| T-01 | Finnhub 갱신 실패 시 만료 캐시가 체결에 사용됨 | P0 | 거래 전용 가격 경로, fetch TTL/공급자 시각 검증, 모든 DB 잠금 획득 후 재검증 | 시세 장애·429·누락/미래/오래된 시각·잠금 대기 후 만료 테스트 통과 | 시장 종료·120초 초과 지연 시 신규 체결 중단; 별도 장외 정책 없음 |
| T-02 | Dashboard의 중복·순차 시세 요청, 숨김 폴링, 오류 은폐 | P1 | 공유 TTL/동시 요청 합치기/최대 4개 동시성/취소/백오프/부분 오류/시각 표시 | 프론트 시세·멱등성 테스트 통과, 서버 stale metadata 테스트 4개 통과 | 새 계측은 미배포; 운영 호출량 전후 비교와 실제 요금 측정은 후속 |
| T-03 | BigDecimal 저장 잔액을 double로 재계산, 시장 가격과 저장 단가의 정밀도 차이 | P0 | 매매·송금·보상·일일 로그인 계산을 BigDecimal로 전환, 체결가 6자리 정규화 | 큰 잔액 센트·반올림·NaN/Infinity·범위 초과·0센트·6자리 단가 테스트 통과 | 일부 API 숫자/평가액/랭킹 표시에는 double 계약 유지 |
| T-04 | 지정가 선두 500행/40종목 반복으로 후순위 종목 누락 가능 | P0 | DISTINCT 종목 cursor 순환, 장애 시에도 cursor 진행, 실제 체결 직전 가격 재검증 | 40종목 이후 도달·wrap 중복 방지·시세 장애 테스트 통과 | 완료 후 5분 대기와 최대 40종목; 종목 수/장애에 따라 추가 지연 |
| L-01 | 매매·송금·지정가 생성 재전송에 중복 방지 계약 부재 | P0 | V11 영수증과 `Idempotency-Key`, 응답 재생, 다른 payload/operation 재사용 시 409 | 실제 MySQL 동시 중복 매수·송금·지정가 생성 테스트 통과 | 키 없는 이전 프론트는 호환 허용하므로 중복 방지 미적용 |
| L-02 | 동시 매매·반대방향 송금·취소/체결 경합 및 부분 실패 | P0 | 회원→지갑 잠금 순서 정렬, 지갑/보유/내역/원장/영수증의 동일 tx | 실제 MySQL 동시 매수·매도·반대 송금·중복 체결·취소 경합·원장/알림 실패 rollback 통과 | 분산 부하/장시간 운영 관측은 미수행 |
| L-03 | 수량 overflow 및 0센트 매수로 불가능한 보유량 발생 가능 | P0 | mutation 전 long 수량 검사·양수 센트 총액 검사 | overflow·0센트 거부 및 상태 무변경 통과 | 매우 큰 숫자의 브라우저 표시는 별도 한계 |
| A-01 | 기존 이메일 인증 횟수/24시간 한도·JWT·refresh 회귀 | P1 | 최신 main의 V10/기존 수정 재사용, 신규 중복 구현 없음 | 기존 인증·회전·쿠키·시도 횟수 tx·메일 한도 회귀 통과 | 실제 SMTP/Google/다중 기기 운영 E2E 미완료 |
| A-02 | STOMP CONNECT가 복사 accessor에만 Principal을 설정 | P1 | transport의 원본 accessor와 user-change callback 보존 | CONNECT 세션 사용자 전달·본인 알림 구독·타인/익명 거부·게스트 채팅 4개 통과 | 운영 알림 재연결은 승인된 배포 후 확인 |
| O-01 | Render SMTP 비밀번호 미지정으로 전체 health DOWN | P1 | 원인 진단·문서 반영; 운영 secret 변경 안 함 | readiness 200, 전체 health 503, MailHealthIndicator 로그로 원인 확인 | Render Secret 설정 및 승인된 반영·실제 메일 발송 필요 |
| O-26 | #26 readiness/Build Filter 수동 설정 잔여 여부 | P1 | API로 readiness와 Included Paths 4개 설정 확인, 문서 정정 | 서비스 설정 확인 | 실제 backend/frontend/Dockerfile deploy와 docs-only skip 검증 후 이슈 종료 |
| O-76 | #76 Production SHA/CSP/브라우저 회귀 | P1 | 실제 Production SHA 재확인, 공개 화면/보호 API smoke | 홈·TradingView·게시판·지갑 공개 화면, 보호 API 401/no-store 확인 | 새 코드 Production 미배포, 인증된 전체 E2E·모바일·CSP enforce 판단 보류 |
| O-78 | #78 trusted proxy 운영 활성화 | P1 | 기존 필터·spoof 회귀 유지; 운영 양측 secret 활성화 안 함 | 기존 프록시 헤더 회귀 통과 | Vercel header 전달 검증→승인된 배포→Render 동기화 및 실제 IP/rate-limit 검증 필요 |
| S-01 | 비밀정보·CSP·커뮤니티/관리자 회귀 | P1 | 필요 없는 재구현 없이 기존 보안 계약 검증 | 492개 Git commit 고신뢰 credential 패턴 파일 0; 커뮤니티/신고/알림/인증 테스트 통과 | 패턴 스캔은 모든 형태의 비밀값 부재를 증명하지 않음; 운영 전체 인증 E2E 미수행 |

## 시세 정책과 정합성 근거

거래용 시세는 fetch cache가 **60초 미만**, Finnhub `t`가 **120초 이내**, 미래 오차가 **30초 이하**여야 한다.
유효 가격은 HALF_UP 소수 6자리로 정규화하고 0이 되면 거부한다. 표시용 stale fallback은 체결 경로에 들어가지 않는다.
가격을 확보한 뒤 DB 잠금 대기가 생기면 실제 mutation 전에 다시 검증한다.

회귀값 `0.1234567 × 1,000,000`은 체결가 `0.123457`로 통일해 총액 `123457.00`과 저장 내역의 재계산이 일치한다.
`0.0000001`은 저장 가격 0이 되므로 거래하지 않는다.
가격 `0.006 × 1`은 현금 `0.01`, 평균매입가 `0.006000`으로 시장가/지정가가 일치한다.

DB 영수증 UNIQUE insert가 동일 사용자/키의 요청을 직렬화한다. 성공 응답과 mutation을 같이 commit하고,
실패 응답/예외는 영수증까지 rollback한다. 같은 키의 성공 요청은 서버 재시작 후에도 저장 응답을 재생한다.
PIN·비밀번호·토큰은 영수증 지문/응답 저장에 넣지 않는다.
키 없는 요청까지 중복 실행이 방지된다고 주장하지 않는다.

## 호출·부하와 오류 표시

기존 Dashboard에는 기본 12종목, 상단 4종목, 선택 1종목의 별도 요청 경로와 포트폴리오 변경 후 재호출이 있었다.
수정 후 화면과 보유 종목의 unique 심볼 집합을 공유하고 30초 TTL과 최대 4개 동시성을 사용한다.
같은 종목 동시 소비자 12개가 한 외부 요청을 공유하는 테스트 및 429 시 대기 중 요청 중단 테스트를 통과했다.
이 수치는 테스트 시나리오의 요청 수이며 실제 운영 트래픽의 절감률로 해석하지 않는다.

서버는 표시용 시세에 `stale`/`fetchedAt`과 대응 헤더를 추가한다.
Micrometer `tardis.finnhub.requests`는 `display_quote`/`trading_price`와 `success`/`failure`/`rate_limited`를 구분한다.
`tardis.market.quote.responses`는 live/fresh-cache/stale 응답을 구분한다.
지금은 운영에 반영되지 않았으며 공개 Actuator 노출 범위를 늘리지 않았다.
글로벌 Finnhub 예산의 분산 집행·대규모 부하·실제 비용 측정은 수행하지 않았다.

잔액/원장/보유 종목 조회 실패는 이전 성공값과 오류를 함께 표시한다. 초기 조회 실패를 0원/기록 없음으로 바꾸지 않는다.
수정한 로컬 지갑을 브라우저에서 확인했으며, backend에 연결할 수 없을 때 `조회 대기`/`거래 원장을 확인할 수 없습니다`와 재조회 버튼이 표시됐다.
로컬 검증은 실패 UI 경로이며 정상 인증 거래 전체 E2E를 대체하지 않는다.

## 검증 결과

- Backend: **254개 성공, 0 실패, 0 error, 0 skip**, `bootJar` 성공.
- 위 결과 안에 실제 MySQL **17개 통합 테스트** 포함. MySQL 8.4/InnoDB, Flyway V1~V11 migrate/validate와 Hibernate validate 성공.
- Frontend: production build 성공; 시세/요청 키 Node 테스트 **14개 성공**; 전체 lint **0 error / 0 warning**. 기존 main의 66 errors / 9 warnings를 필요한 부분만 patch해 정리했고 규칙을 비활성화하지 않았다.
- `git diff --check` 통과. V1~V10 SQL 변경 없음. 새 V11은 테이블 추가만 포함한다.
- GitHub 최근 5개 실행은 성공이며 조회된 최근 실패 3개는 2026-09-22의 이전 SHA에 대한 CI이다. 수동 fallback 정책을 유지하고 새 workflow를 실행하지 않았다.
- CSP는 Production 응답에서 Report-Only임을 확인했다. 이번 CSP 위반 문자열 로그 조회 결과 0건은 무위반 증명이 아니므로 enforce로 전환하지 않았다.
- 운영 데이터·실제 사용자·비밀값을 테스트에 사용하지 않았다. MySQL 검증은 loopback 전용 임시 DB에서 실행했다.

실행법과 계약 상세는 [TRADE_INTEGRITY.md](TRADE_INTEGRITY.md), migration 정책은 [DB_MIGRATION.md](DB_MIGRATION.md)를 참조한다.

## 승인·사용자 설정이 필요한 잔여 작업

1. Render Dashboard Secret에서 유효한 `MAIL_USERNAME`/`MAIL_PASSWORD` 복구. 값을 채팅·Git에 복사하지 않고 승인된 운영 반영 후 실제 인증메일 발송과 전체 health 확인.
2. #78은 Vercel의 upstream secret-header 전달을 먼저 검증한 뒤 승인된 Vercel Production 롤아웃과 Render secret 동기화를 수행. Render secret만 먼저 활성화하지 않음.
3. #76은 사용자 승인 후 새 프론트를 반영하고 로그인/refresh/멀티탭/Google/Board/Wallet/Watchlist/Stock/STOMP/관리자/모바일 E2E와 CSP 로그 수집.
4. #26은 Dashboard 설정 자체는 이미 확인됨. 다음 자연스러운 변경에서 deploy/skip 동작만 확인. 이 검증만을 위한 불필요한 운영 배포는 하지 않음.
5. 수정 프론트 운영 반영 후 키 필수화 검토. 영수증 보관·삭제 정책은 늦은 재전송의 중복 실행 가능성을 고려해 별도 설계.

이번 검증 범위에서 잔액·보유 수량·거래 내역·원장의 원자성과 외부 시세 장애 시 무체결을 확인했다.
운영 미반영 코드와 미검증 운영 흐름까지 포함한 무조건적 보장은 하지 않는다.
