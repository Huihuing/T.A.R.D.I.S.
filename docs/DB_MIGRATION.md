# Database Migration Plan / DB 마이그레이션 계획

현재 운영 DB는 기존 Hibernate `ddl-auto=update` 흐름으로 생성·변경되어 왔습니다.

운영 데이터가 존재하는 상태에서 현재 스키마를 확인하지 않고 Flyway 초기 SQL을 강제로 적용하면
서비스 기동 실패 또는 데이터 손상 위험이 있으므로, 공개 운영 DB에는 즉시 스키마 변경을 강제하지 않습니다.

## 현재 안전장치

- `JPA_DDL_AUTO` 환경변수로 정책을 변경할 수 있습니다.
- 기본값은 기존 호환성을 위해 `update`입니다.
- 운영 전환 시 현재 Aiven MySQL schema dump를 먼저 확보해야 합니다.

## Flyway 전환 절차

1. Aiven MySQL의 현재 테이블/인덱스/제약조건을 schema-only dump로 보관합니다.
2. dump에서 비밀정보와 실제 사용자 데이터를 제외합니다.
3. 현재 스키마를 Flyway baseline으로 기록합니다.
4. 개발/복제 DB에서 `ddl-auto=validate` + Flyway로 기동 테스트합니다.
5. CI에서 마이그레이션 후 Spring Boot 테스트를 실행합니다.
6. 운영 DB 백업을 확인한 뒤 Render의 `JPA_DDL_AUTO=validate`로 전환합니다.
7. 이후 모든 DB 변경은 `V2__...`, `V3__...` 식 migration으로 관리합니다.

## 아직 자동 전환하지 않은 이유

현재 연결된 관리 도구에서는 운영 MySQL의 실제 DDL을 안전하게 읽어 baseline과 비교할 수 없습니다.
따라서 스키마를 추측해 migration을 작성하는 것보다 기존 데이터를 보존하는 것이 우선입니다.


## 코드 준비 상태

Flyway 전환을 위해 애플리케이션 의존성과 설정 골격은 추가되어 있습니다.
기본값은 운영 호환성을 위해 다음과 같이 유지합니다.

```text
FLYWAY_ENABLED=false
JPA_DDL_AUTO=update
```

따라서 현재 배포에서는 Flyway가 스키마를 변경하지 않습니다.
운영 DB의 schema-only dump를 확보하고 baseline SQL을 검증한 뒤에만 아래처럼 전환합니다.

```text
FLYWAY_ENABLED=true
JPA_DDL_AUTO=validate
```

### 전환 체크리스트

- 현재 Aiven MySQL schema-only dump 확보
- 테이블/인덱스/FK/unique/default/charset/collation 비교
- 개발용 복제 DB에 baseline 적용
- 애플리케이션을 `ddl-auto=validate`로 기동
- 전체 백엔드 테스트 통과 확인
- 운영 DB 백업 확인
- Render 환경변수 전환
- 이후 스키마 변경은 버전 migration만 사용

### 금지 사항

- 운영 DB 스키마를 추측해서 `V1__baseline.sql`을 작성하지 않습니다.
- 실제 dump 확인 전 `FLYWAY_ENABLED=true`로 전환하지 않습니다.
- Flyway와 Hibernate `ddl-auto=update`를 동시에 스키마 변경 도구로 사용하지 않습니다.
