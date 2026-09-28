# Database Migration / DB 마이그레이션

## 현재 운영 상태

T.A.R.D.I.S. 운영 Aiven MySQL은 2026-09-28 Hibernate `ddl-auto=update` 중심 운영에서 Flyway 기반 스키마 관리로 전환했습니다.

현재 정책은 다음과 같습니다.

```text
Flyway: enabled
Flyway schema version: 7
baseline-on-migrate: false
Hibernate ddl-auto: validate
Database: Aiven MySQL 8.4.8 / defaultdb
```

이제 Hibernate는 운영 테이블을 자동 생성/수정하지 않습니다. 애플리케이션 시작 시 Flyway가 migration 이력을 검증하고 필요한 새 migration을 실행한 뒤 Hibernate가 엔티티와 DB 스키마 정합성을 검증합니다.

## 2026-09-28 전환 기록

Aiven 관리 커넥터에는 MySQL 임의 SQL 실행 기능이 없어, Render 백엔드의 기존 DB 연결을 이용하는 일회성 schema-only 진단 Runner로 운영 DDL을 수집했습니다. 이 Runner는 사용자 행 데이터를 조회하지 않고 `SHOW CREATE TABLE` 등 스키마 정보만 사용했으며, 전환 완료 후 제거했습니다.

확인된 pre-Flyway 운영 테이블은 17개였습니다.

- `member`
- `post`
- `comment`
- `bookmark`
- `community_report`
- `email_verification`
- `ledger_entry`
- `limit_order`
- `notifications`
- `password_reset_code`
- `portfolio`
- `portfolio_snapshot`
- `price_alert`
- `refresh_token`
- `trade_history`
- `user_economy`
- `wallet`

실제 DDL을 기준으로 `V1__baseline.sql`을 작성했습니다. V1은 Flyway 도입 직전 운영 구조를 그대로 기록하며 당시 존재하던 legacy 제약도 의도적으로 보존합니다.

기존 운영 DB는 schema history table 생성 후 version 1로 baseline했고, 이후 V2~V7을 순차 적용했습니다.

## 적용된 migration

### V1: 실제 운영 baseline

- 17개 운영 테이블 실제 DDL 기록
- 사용자 데이터나 비밀정보를 포함하지 않음
- 기존 운영 DB는 version 1로 baseline

### V2: 게스트 커뮤니티 스키마 정합성

운영 DDL과 애플리케이션 모델 사이의 실제 불일치를 수정했습니다.

```sql
ALTER TABLE post
    MODIFY COLUMN member_id bigint NULL;

ALTER TABLE comment
    MODIFY COLUMN member_id bigint NULL;

ALTER TABLE comment
    MODIFY COLUMN content text NOT NULL;
```

변경 후 Hibernate `ddl-auto=validate`가 통과했습니다.

### V3: 지갑/원장 금액 정밀도

현금과 원장 값은 센트 정밀도로 고정했습니다.

- `wallet.balance` → `DECIMAL(19,2)`
- `ledger_entry.amount` → `DECIMAL(19,2)`
- `ledger_entry.balance_after` → `DECIMAL(19,2)`
- Java 저장 필드 → `BigDecimal`

### V4: 자산 스냅샷 금액 정밀도

- `portfolio_snapshot.cash_balance` → `DECIMAL(19,2)`
- `portfolio_snapshot.invested_value` → `DECIMAL(19,2)`
- `portfolio_snapshot.total_assets` → `DECIMAL(19,2)`
- Java 저장 필드 → `BigDecimal`

스냅샷 금액은 저장 전 HALF_UP 센트 반올림을 적용합니다.

### V5: 지정가 주문 / 가격 알림

사용자 입력 기준 가격은 센트 단위 정책으로 통일했습니다.

- `limit_order.limit_price` → `DECIMAL(19,2)`
- `limit_order.fill_price` → `DECIMAL(19,2)` nullable
- `price_alert.target_price` → `DECIMAL(19,2)`
- Java 저장 필드 → `BigDecimal`

반올림 후 `$0.01` 미만이 되는 입력은 주문/알림 생성 단계에서 거부하도록 회귀 테스트를 추가했습니다.

### V6: 시장 유래 가격

시장 체결가·평균매입가는 센트보다 높은 정밀도를 보존합니다.

- `portfolio.average_price` → `DECIMAL(19,6)`
- `trade_history.price` → `DECIMAL(19,6)`
- Java 저장 필드 → `BigDecimal`

기존 public API의 `double` 계약은 유지하면서 DB 저장 시 6자리 HALF_UP 정규화를 적용합니다.

### V7: 북마크 저장 가격

북마크에 기록되는 시장 가격도 다른 시장 유래 가격과 동일한 6자리 정책으로 통일했습니다.

- `bookmark.price` → `DECIMAL(19,6)`
- Java 저장 필드 → `BigDecimal`
- 기존 constructor/getter/setter의 `double` API 계약 유지
- 7번째 소수 자리 HALF_UP 경계 테스트 추가

운영 Render 로그에서 다음 순서를 확인했습니다.

1. Aiven MySQL 8.4 연결
2. 기존 schema version `6` 확인
3. `Migrating schema ... to version "7 - bookmark price decimal"`
4. `Successfully applied 1 migration ... now at version v7`
5. 애플리케이션 정상 기동
6. Render deployment `live`

## 금액/가격 정밀도 정책

현재 persistence 기준은 다음과 같습니다.

| 도메인 | DB 타입 | Java 저장 타입 | 규칙 |
| --- | --- | --- | --- |
| 지갑 잔액 | `DECIMAL(19,2)` | `BigDecimal` | HALF_UP 센트 |
| 원장 금액/잔액 | `DECIMAL(19,2)` | `BigDecimal` | HALF_UP 센트 |
| 자산 스냅샷 | `DECIMAL(19,2)` | `BigDecimal` | HALF_UP 센트 |
| 지정가/체결가(지정가 주문) | `DECIMAL(19,2)` | `BigDecimal` | 사용자 주문 단위 센트 |
| 가격 알림 목표가 | `DECIMAL(19,2)` | `BigDecimal` | 사용자 입력 단위 센트 |
| 포트폴리오 평균매입가 | `DECIMAL(19,6)` | `BigDecimal` | 시장 가격 6자리 |
| 거래 체결가 | `DECIMAL(19,6)` | `BigDecimal` | 시장 가격 6자리 |
| 북마크 저장 가격 | `DECIMAL(19,6)` | `BigDecimal` | 시장 가격 6자리 |

API 호환성을 위해 일부 getter/DTO 경계는 여전히 `double`을 사용하지만, 영속 저장 값은 고정소수점 타입을 사용합니다.

공통 현금 반올림은 `MoneyMath.roundCents`의 `BigDecimal.valueOf(...).setScale(2, HALF_UP)` 규칙을 사용합니다. 원장, 경제 보상, 지정가 주문, 가격 알림, 자산 스냅샷 등에 같은 규칙을 적용하도록 회귀 테스트를 추가했습니다.

## 이메일 UNIQUE 제약 — 현재 보류

`member.email`은 애플리케이션에서 중복 사용을 막는 정책이지만 pre-Flyway 실제 DB에는 UNIQUE 제약이 없었습니다.

개인정보를 출력하지 않는 aggregate audit 결과는 다음과 같았습니다.

```text
totalMembers=2
distinctEmails(case-insensitive)=1
duplicateGroups=1
emailCollation=utf8mb4_0900_ai_ci
```

현재 운영 데이터에는 같은 이메일로 간주되는 기존 회원 행이 1개 그룹 존재합니다. 따라서 지금 `UNIQUE(email)`을 추가하면 migration이 실패할 수 있으므로 자동 적용하지 않습니다.

원칙:

- 기존 두 계정을 자동 삭제/병합하지 않습니다.
- 어느 계정의 이메일을 변경할지 시스템이 임의 결정하지 않습니다.
- 사용자 데이터 정리 후 중복이 0인지 aggregate로 다시 확인합니다.
- 그 다음 `V8__...` 이상의 별도 migration으로 DB UNIQUE 제약을 추가합니다.

일반 이메일 회원가입 경로는 `email_verification`의 이메일 unique row와 pessimistic lock으로 인증 consume을 직렬화해 동시 가입 race를 완화하고 있습니다. 최종 불변조건은 기존 중복 정리 후 DB UNIQUE로 다시 검토합니다.

## 인덱스 후속 후보

실제 baseline에서 이미 존재함을 확인한 주요 인덱스:

- `refresh_token(token_hash)` unique
- `refresh_token(member_id)`
- `limit_order(status, symbol)`
- `limit_order(member_id, created_at)`
- `price_alert(active, symbol)`
- `portfolio_snapshot(member_id, captured_at)`
- `ledger_entry(member_id, created_at)`

Repository 조회 패턴상 추가 검토 후보:

| 테이블 | 후보 인덱스 | 사용 패턴 |
| --- | --- | --- |
| `notifications` | `(member_id, created_at)` | 사용자별 최근 알림 |
| `notifications` | `(member_id, type, created_at)` | 사용자+유형 최근 알림 |
| `notifications` | `(member_id, read_at)` | 미읽음 조회/count |
| `price_alert` | `(member_id, created_at)` | 사용자별 가격 알림 목록 |
| `price_alert` | `(member_id, active)` | 사용자별 활성 알림 |
| `price_alert` | `(active, created_at)` | 스케줄러 batch |
| `post` | `(created_at)` | 최신순 pagination |
| `post` | `(member_id, created_at)` | 사용자 활동 조회 |
| `comment` | `(post_id, created_at)` | 게시글 댓글 시간순 |
| `comment` | `(member_id, created_at)` | 사용자 활동 조회 |
| `trade_history` | `(member_id, trade_type, trade_time)` | 일일 거래 활동 조회 |

인덱스는 데이터 규모와 실제 실행계획을 확인한 뒤 추가합니다. 게시판의 `%검색어%` contains 검색은 일반 B-tree 인덱스로 큰 이득을 보기 어렵기 때문에 병목이 확인될 때 MySQL FULLTEXT나 별도 검색 구조를 검토합니다.

## 앞으로의 migration 규칙

1. 운영에 적용된 V1~V7 파일은 수정하지 않습니다.
2. 모든 DB 스키마 변경은 `V8__...` 이상의 새 migration으로 추가합니다.
3. 운영에서 Hibernate `ddl-auto=update/create/create-drop`를 사용하지 않습니다.
4. migration 적용 전 데이터 손실 가능성과 lock 시간을 검토합니다.
5. 타입 축소, 컬럼 삭제, 대량 데이터 재작성은 Aiven 백업과 별도 검증 후 진행합니다.
6. migration SQL에 비밀정보나 실제 사용자 데이터를 하드코딩하지 않습니다.
7. 이미 적용된 migration을 고치는 대신 새 보정 migration을 추가합니다.
8. Render Docker build의 frontend production build, backend tests, `bootJar`를 통과한 뒤 운영 기동을 확인합니다.
9. Flyway 적용 후 Hibernate validate, Render `live`, error/fatal 로그를 확인합니다.

## 운영 체크리스트

새 DB migration을 추가할 때 확인:

- migration 버전이 기존 이력과 충돌하지 않는지
- 이미 운영에 적용된 migration 파일을 수정하지 않았는지
- SQL이 데이터 삭제/축소를 포함하는지
- 필요한 경우 Aiven 최신 백업이 존재하는지
- frontend production build가 통과하는지
- backend tests와 `bootJar`가 통과하는지
- 새 인스턴스에서 Flyway validate/migrate가 성공하는지
- Hibernate `ddl-auto=validate`가 성공하는지
- Render가 최종 `live`가 되는지
- 배포 구간에 error/fatal 로그가 없는지

현재 DB 스키마 관리의 기준은 Flyway이며 Hibernate는 검증 전용입니다.
