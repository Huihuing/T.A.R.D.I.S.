# Flyway migrations

운영 Aiven MySQL(`defaultdb`)에 적용되는 검증된 migration SQL만 둡니다.
운영 이력, 정밀도 정책, 체크리스트는 [`docs/DB_MIGRATION.md`](../../../../../../docs/DB_MIGRATION.md)를 봅니다.

## 규칙

- 이미 운영에 적용된 파일은 수정하지 않습니다. Flyway checksum이 운영 이력에 기록되어 있습니다.
- 스키마 변경은 이 디렉터리의 가장 큰 번호 다음 `V{n}__설명.sql`로 추가합니다.
- `spring.flyway.baseline-on-migrate=false`, `spring.jpa.hibernate.ddl-auto=validate`를 유지합니다.
- 비밀정보나 실제 사용자 데이터를 넣지 않습니다.
- 데이터 삭제, 타입 축소, 대량 변환은 사용자 승인과 Aiven 백업 확인 후 진행합니다.
- 배포 후 Flyway 적용, Hibernate validate, Render `live`, error/fatal 로그를 확인합니다.

## 파일

| 버전 | 내용 |
| --- | --- |
| V1 | Flyway 도입 직전 17개 운영 테이블 baseline (운영 DB는 version 1로 baseline) |
| V2 | 게스트 커뮤니티: `post/comment.member_id` nullable, `comment.content` `TEXT NOT NULL` |
| V3 | `wallet`, `ledger_entry` 금액 `DECIMAL(19,2)` |
| V4 | `portfolio_snapshot` 금액 `DECIMAL(19,2)` |
| V5 | `limit_order`, `price_alert` 가격 `DECIMAL(19,2)` |
| V6 | `portfolio.average_price`, `trade_history.price` `DECIMAL(19,6)` |
| V7 | `bookmark.price` `DECIMAL(19,6)` |
| V8 | case-insensitive 중복 이메일 비파괴 격리 후 `member.email` UNIQUE |
| V9 | 사용자 승인에 따른 1회성 운영 데이터 전체 초기화 (스키마·history 유지) |
| V10 | 이메일 인증번호 24시간 발송 한도 테이블 `email_code_send_quota` |
