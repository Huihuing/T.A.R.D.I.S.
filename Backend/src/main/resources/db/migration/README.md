# Flyway migrations

이 디렉터리는 검증된 DB migration SQL만 저장합니다.

## 현재 운영 상태

2026-09-28 실제 Aiven MySQL 운영 스키마를 schema-only로 확인한 뒤 Flyway 전환을 완료했습니다.

현재 운영 기준:

- Database: Aiven MySQL 8.4.8 / `defaultdb`
- Flyway schema version: `7`
- `spring.flyway.enabled=true`
- `spring.flyway.baseline-on-migrate=false`
- `spring.jpa.hibernate.ddl-auto=validate`
- Render 배포에서 backend tests + `bootJar` 실행 후 새 인스턴스를 기동

운영 DB는 version 1로 baseline된 뒤 V2~V7을 순차 적용했습니다. V7 배포 로그에서 기존 version 6 확인 후 `7 - bookmark price decimal` migration이 성공했고, schema version이 v7이 된 것을 확인했습니다.

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

Java 엔티티의 해당 저장 필드는 `BigDecimal`을 사용합니다. 기존 프론트/API 호환성을 위해 일부 public getter/constructor는 `double` 계약을 유지하되 저장 시 도메인별 scale과 HALF_UP 반올림 규칙을 적용합니다.

## 현재 보류 항목

`member.email`에는 아직 DB UNIQUE 제약을 추가하지 않았습니다. 기존 운영 데이터 aggregate audit에서 case-insensitive 기준 중복 이메일 1그룹이 확인되어, 자동 삭제/병합 없이 기존 데이터를 먼저 정리해야 합니다.

중복이 0임을 다시 확인하기 전에는 `UNIQUE(email)` migration을 추가하지 않습니다.

## 규칙

- 운영에 적용된 기존 migration 파일은 수정하지 않습니다. Flyway checksum이 이미 운영 이력에 기록되어 있습니다.
- 이후 스키마 변경은 반드시 `V8__...`처럼 새 migration으로 추가합니다.
- Hibernate `ddl-auto=update/create/create-drop`로 운영 스키마를 변경하지 않습니다.
- migration에는 비밀정보나 실제 사용자 데이터를 포함하지 않습니다.
- 파괴적 변경, 타입 축소, 대량 데이터 변환은 사전 백업/검증 후 별도 migration으로 진행합니다.
- 새 migration 배포 후 Flyway 적용, Hibernate validate, Render `live`, error/fatal 로그를 확인합니다.

세부 운영 이력과 남은 DB 작업은 `docs/DB_MIGRATION.md`를 참고하세요.
