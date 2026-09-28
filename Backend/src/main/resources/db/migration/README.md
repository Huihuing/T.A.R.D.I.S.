# Flyway migrations

이 디렉터리는 검증된 DB migration SQL만 저장합니다.

## 현재 운영 상태

2026-09-28 실제 Aiven MySQL 운영 스키마를 schema-only로 확인한 뒤 Flyway 전환을 완료했습니다.

- `V1__baseline.sql`: Flyway 도입 직전의 17개 운영 테이블 구조를 기록한 baseline
- `V2__align_guest_community_schema.sql`: 게스트 게시글/댓글 스키마 정합성 수정
  - `post.member_id` nullable
  - `comment.member_id` nullable
  - `comment.content` `TEXT NOT NULL`
- 운영 DB의 Flyway schema version: `2`
- `spring.flyway.enabled=true`
- `spring.flyway.baseline-on-migrate=false`
- `spring.jpa.hibernate.ddl-auto=validate`

운영 DB는 version 1로 baseline된 뒤 V2가 적용되었으며, 이후 재기동에서 `Schema ... is up to date. No migration necessary.`를 확인했습니다.

## 규칙

- 운영에 적용된 `V1`/`V2` 파일은 수정하지 않습니다. Flyway checksum이 이미 운영 이력에 기록되어 있습니다.
- 이후 스키마 변경은 반드시 `V3__...`, `V4__...`처럼 새 migration으로 추가합니다.
- Hibernate `ddl-auto=update/create/create-drop`로 운영 스키마를 변경하지 않습니다.
- migration에는 비밀정보나 실제 사용자 데이터를 포함하지 않습니다.
- 파괴적 변경, 타입 축소, 대량 데이터 변환은 사전 백업/검증 후 별도 migration으로 진행합니다.

세부 운영 이력과 남은 DB 작업은 `docs/DB_MIGRATION.md`를 참고하세요.
