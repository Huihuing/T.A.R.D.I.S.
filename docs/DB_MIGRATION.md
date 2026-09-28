# Database Migration / DB 마이그레이션

## 현재 운영 상태

T.A.R.D.I.S. 운영 Aiven MySQL은 2026-09-28 Hibernate `ddl-auto=update` 중심 운영에서 Flyway 기반 스키마 관리로 전환했습니다.

현재 정책은 다음과 같습니다.

```text
Flyway: enabled
Flyway schema version: 2
baseline-on-migrate: false
Hibernate ddl-auto: validate
Database: Aiven MySQL 8.4.x
```

즉, 이제 Hibernate가 운영 테이블을 자동 생성/수정하지 않습니다. 애플리케이션 시작 시 Flyway가 migration 이력을 검증하고 필요한 새 migration만 실행한 뒤 Hibernate가 엔티티와 DB 스키마의 정합성을 검증합니다.

## 2026-09-28 전환 기록

Aiven 관리 커넥터에 임의 MySQL SQL 실행 기능이 없어, Render 백엔드의 기존 DB 연결을 이용하는 일회성 schema-only 진단 Runner로 운영 DDL을 수집했습니다. 이 Runner는 행 데이터를 조회하지 않고 `information_schema`와 `SHOW CREATE TABLE`만 사용했으며, 전환 완료 후 제거했습니다.

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

실제 DDL을 기준으로 `V1__baseline.sql`을 작성했습니다. V1은 Flyway 도입 직전 운영 구조를 그대로 기록하며, 당시 존재하던 legacy 제약도 의도적으로 보존합니다.

운영 DB에는 기존 데이터와 테이블이 이미 있었으므로 최초 Flyway 실행에서 schema history table을 생성하고 version 1로 baseline했습니다. 이후 V2를 실행했습니다.

실제 운영 로그에서 다음 순서를 확인했습니다.

1. Flyway가 Aiven MySQL 8.4에 연결
2. migration 검증 성공
3. `flyway_schema_history` 생성
4. `Successfully baselined schema with version: 1`
5. `V2__align_guest_community_schema.sql` 실행
6. `Successfully applied 1 migration ... now at version v2`
7. Hibernate `ddl-auto=validate` 성공
8. 애플리케이션 정상 기동
9. 후속 재배포에서 `Current version ...: 2` 및 `Schema ... is up to date. No migration necessary.` 확인
10. `baseline-on-migrate=false`로 잠금

## V2: 게스트 커뮤니티 스키마 정합성

운영 DDL을 직접 확인하면서 애플리케이션 모델과 DB 사이의 실제 불일치를 발견했습니다.

- `post.member_id`가 DB에서 `NOT NULL`이어서 게스트 게시글 모델과 충돌
- `comment.member_id`가 DB에서 `NOT NULL`이어서 게스트 댓글 모델과 충돌
- `comment.content`가 `VARCHAR(255)`인데 API는 댓글을 최대 3,000자까지 허용

V2는 데이터 삭제나 타입 축소 없이 다음 widening/relaxing 변경만 수행합니다.

```sql
ALTER TABLE post
    MODIFY COLUMN member_id bigint NULL;

ALTER TABLE comment
    MODIFY COLUMN member_id bigint NULL;

ALTER TABLE comment
    MODIFY COLUMN content text NOT NULL;
```

`Comment.content` JPA 매핑도 `TEXT` 기대값으로 맞췄고, 이후 Hibernate validate가 통과했습니다.

## 앞으로의 migration 규칙

운영에 적용된 `V1__baseline.sql`과 `V2__align_guest_community_schema.sql`은 수정하지 않습니다. Flyway checksum이 운영 이력에 기록되어 있기 때문입니다.

앞으로는 다음 원칙을 적용합니다.

1. 모든 DB 스키마 변경은 새 Flyway migration으로 추가합니다.
2. 다음 migration은 `V3__...`부터 시작합니다.
3. 운영에서 Hibernate `ddl-auto=update/create/create-drop`를 사용하지 않습니다.
4. migration 적용 전 변경의 데이터 손실 가능성과 lock 시간을 검토합니다.
5. 타입 축소, 컬럼 삭제, 대량 데이터 재작성은 Aiven 백업과 별도 검증 후 진행합니다.
6. migration SQL에 비밀정보나 실제 사용자 데이터를 하드코딩하지 않습니다.
7. 이미 적용된 migration 파일을 고치는 대신 새 보정 migration을 추가합니다.

## 이메일 UNIQUE 제약 — 현재 보류

`member.email`은 애플리케이션에서 중복 사용을 막는 정책이지만 pre-Flyway 실제 DB에는 UNIQUE 제약이 없었습니다.

DB UNIQUE 추가 전 개인정보를 출력하지 않는 aggregate audit을 수행한 결과는 다음과 같습니다.

```text
totalMembers=2
distinctEmails(case-insensitive)=1
duplicateGroups=1
emailCollation=utf8mb4_0900_ai_ci
```

즉 현재 운영 데이터에는 같은 이메일로 간주되는 기존 회원 행이 1개 그룹 존재합니다. 따라서 지금 `UNIQUE(email)`을 추가하면 migration이 실패할 수 있으므로 V3로 바로 추가하지 않습니다.

주의 사항:

- 기존 두 계정을 자동 삭제/병합하지 않습니다.
- 어느 계정의 이메일을 변경할지 시스템이 임의 결정하지 않습니다.
- 사용자 데이터 정리 후 중복이 0인지 다시 aggregate로 확인합니다.
- 그 다음 별도 migration으로 DB UNIQUE 제약을 추가합니다.

일반 이메일 회원가입 경로는 `email_verification`의 이메일 unique row와 pessimistic lock을 사용해 인증 consume을 직렬화하므로 현재 코드에서도 동시 가입 race가 상당히 완화되어 있습니다. 그래도 최종 불변조건은 DB UNIQUE가 담당하는 것이 바람직하므로 기존 중복 정리 후 반드시 다시 검토합니다.

## 금액/가격 정밀도 — 후속 migration

현재 여러 금액/가격 필드는 Java `double` 및 MySQL `DOUBLE`을 사용합니다. 현금 흐름 일부는 코드에서 센트 단위 반올림을 하지만 장기적으로는 정밀 금액 타입으로 전환하는 편이 안전합니다.

후속 후보:

- `wallet.balance`: `DECIMAL(19,2)` + `BigDecimal`
- `ledger_entry.amount`: `DECIMAL(19,2)` + `BigDecimal`
- `ledger_entry.balance_after`: `DECIMAL(19,2)` + `BigDecimal`
- `portfolio.average_price`: 주가 정밀도 정책에 맞춘 `DECIMAL(19,4~6)` + `BigDecimal`
- `trade_history.price`: 동일 주가 정밀도 정책 적용
- `limit_order.limit_price` / `fill_price`: 동일 주가 정밀도 정책 적용
- `price_alert.target_price`: 동일 주가 정밀도 정책 적용
- `portfolio_snapshot.cash_balance` / `invested_value` / `total_assets`: 현금/평가액 도메인 규칙을 먼저 정한 뒤 적용

이 작업은 단순 DB 타입 변경으로 끝내지 않습니다. Java DTO/API 직렬화, 반올림 모드, 소수점 자리수, 기존 DOUBLE 데이터 변환 정책을 먼저 정의한 뒤 별도 migration과 코드 변경을 한 의미 단위로 진행합니다.

## 인덱스 후속 후보

실제 baseline에서 이미 존재함을 확인한 주요 인덱스는 다음과 같습니다.

- `refresh_token(token_hash)` unique
- `refresh_token(member_id)`
- `limit_order(status, symbol)`
- `limit_order(member_id, created_at)`
- `price_alert(active, symbol)`
- `portfolio_snapshot(member_id, captured_at)`
- `ledger_entry(member_id, created_at)`

다음은 Repository 조회 패턴상 추가 검토 후보이며 아직 적용하지 않습니다.

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

인덱스는 데이터 규모와 실제 실행계획을 확인한 뒤 추가합니다. 특히 게시판의 `%검색어%` contains 검색은 일반 B-tree 인덱스로 큰 이득을 보기 어렵기 때문에, 병목이 확인될 때 MySQL FULLTEXT나 별도 검색 구조를 검토합니다.

## 운영 체크리스트

새 DB migration을 추가할 때는 다음을 확인합니다.

- migration 이름과 버전이 기존 이력과 충돌하지 않는지
- 기존 V1/V2 파일을 수정하지 않았는지
- SQL이 데이터 삭제/축소를 포함하는지
- 필요한 경우 Aiven 최신 백업이 존재하는지
- Render 빌드에서 backend tests와 bootJar가 통과하는지
- 새 인스턴스에서 Flyway validate/migrate가 성공하는지
- Hibernate `ddl-auto=validate`가 성공하는지
- Render가 최종 `live`가 되는지
- 배포 구간에 error/fatal 로그가 없는지

현재 DB 스키마 관리의 기준은 Flyway이며 Hibernate는 검증 전용입니다.
