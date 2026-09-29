# Flyway migrations

이 디렉터리는 검증된 DB migration SQL만 저장합니다.

## 현재 운영 상태

2026-09-28 실제 Aiven MySQL 운영 스키마를 schema-only로 확인한 뒤 Flyway 전환을 완료했습니다.

현재 운영 기준:

- Database: Aiven MySQL 8.4.8 / `defaultdb`
- Flyway schema version: `10`
- `spring.flyway.enabled=true`
- `spring.flyway.baseline-on-migrate=false`
- `spring.jpa.hibernate.ddl-auto=validate`
- Render 배포에서 backend tests + `bootJar` 실행 후 새 인스턴스를 기동

운영 DB는 version 1로 baseline된 뒤 V2~V10을 순차 적용합니다. V8에서 `member.email` UNIQUE 불변조건을 확립했고, V9은 사용자 명시 승인에 따라 기존 테스트/운영성 데이터를 전부 비우고 깨끗한 초기 상태로 되돌리는 1회성 데이터 reset migration입니다. 스키마와 Flyway history는 유지합니다. V10은 이메일 인증번호 발송 한도 테이블을 추가합니다.

## 적용된 migration

- `V1__baseline.sql`
  - Flyway 도입 직전 17개 운영 테이블의 실제 DDL을 기록한 baseline
  - 기존 운영 DB는 version 1로 baseline
- `V2__align_guest_community_schema.sql`
  - `post.member_id` nullable
  - `comment.member_id` nullable
  - `comment.content` `TEXT NOT NULL`
- `V3__wallet_ledger_decimal.sql`
  - `wallet.balance` → `DECIMAL(19,2)`
  - `ledger_entry.amount` / `balance_after` → `DECIMAL(19,2)`
- `V4__portfolio_snapshot_decimal.sql`
  - `portfolio_snapshot.cash_balance` / `invested_value` / `total_assets` → `DECIMAL(19,2)`
- `V5__order_alert_price_decimal.sql`
  - `limit_order.limit_price` / `fill_price` → `DECIMAL(19,2)`
  - `price_alert.target_price` → `DECIMAL(19,2)`
- `V6__market_price_decimal.sql`
  - `portfolio.average_price` → `DECIMAL(19,6)`
  - `trade_history.price` → `DECIMAL(19,6)`
- `V7__bookmark_price_decimal.sql`
  - `bookmark.price` → `DECIMAL(19,6)`
- `V8__dedupe_member_email_and_add_unique.sql`
  - case-insensitive 중복 이메일 그룹마다 가장 큰 `member.id` 유지
  - 오래된 중복 계정 이메일을 `deduped-member-<id>@example.invalid` 형태의 비활성 고유값으로 격리
  - 사용자/자산/거래/커뮤니티 데이터 삭제 없음
  - `member.email`에 `uk_member_email` UNIQUE 제약 추가
- `V9__reset_operational_data.sql`
  - 사용자 명시 승인으로 17개 애플리케이션 테이블의 기존 행을 전부 삭제
  - FK를 비활성화하지 않고 자식 테이블부터 순서대로 정리
  - 각 테이블 `AUTO_INCREMENT`를 1로 재설정
  - 테이블/인덱스/제약/Flyway schema history는 유지
  - 비밀번호, 관리자 계정, 테스트 계정을 migration에 하드코딩하지 않음

Java 엔티티의 `member.email` 저장 필드는 DB UNIQUE 계약과 동일하게 유지합니다. 일반 회원가입은 사전 case-insensitive 중복 검사와 DB UNIQUE를 함께 사용하며, 동시 요청으로 DB 무결성 충돌이 발생하면 API는 409 Conflict로 처리합니다.

- `V10__email_code_send_quota.sql`
  - 이메일 주소당 24시간 인증번호 발송 횟수를 기록하는 `email_code_send_quota` 테이블 추가
  - `email` UNIQUE(`uk_email_code_send_quota_email`), 기존 테이블/행 변경 없음

관리자 권한은 DB role 컬럼이 아니라 Render의 `admin.usernames` 설정과 로그인 username 일치 여부로 판정하므로, V9 reset 후 해당 username으로 새 계정을 정상 가입하면 다시 관리자 권한을 사용할 수 있습니다.

## 규칙

- 운영에 적용된 기존 migration 파일은 수정하지 않습니다. Flyway checksum이 이미 운영 이력에 기록되어 있습니다.
- 이후 스키마 변경은 반드시 `V11__...`처럼 새 migration으로 추가합니다.
- Hibernate `ddl-auto=update/create/create-drop`로 운영 스키마를 변경하지 않습니다.
- migration에는 비밀정보나 실제 사용자 데이터를 포함하지 않습니다.
- 파괴적 변경, 타입 축소, 대량 데이터 변환은 사용자 승인과 백업/검증 후 별도 migration으로 진행합니다.
- 새 migration 배포 후 Flyway 적용, Hibernate validate, Render `live`, error/fatal 로그를 확인합니다.

세부 운영 이력과 남은 DB 작업은 `docs/DB_MIGRATION.md`를 참고하세요.
