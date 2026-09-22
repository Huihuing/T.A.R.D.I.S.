# Security Policy / 보안 정책

## 지원 범위

현재 `main` 브랜치의 최신 배포를 지원합니다.

## 비밀정보 관리

- API 키, JWT secret, 데이터베이스 비밀번호는 저장소에 커밋하지 않습니다.
- 운영 비밀정보는 Render/Vercel 등 배포 환경변수에서 관리합니다.
- 로컬 개발용 `Backend/src/main/resources/application-api.yaml`은 Git 추적 대상이 아닙니다.
- 예제 설정에는 실제 키 대신 placeholder만 사용합니다.

## 인증 및 권한

- 로그인 성공 시 서버가 서명된 JWT를 발급합니다.
- 보호 API는 `Authorization: Bearer <token>`을 요구합니다.
- JWT 서명, 만료, 변조 거부와 Bearer 필터 동작을 자동 테스트합니다.
- 사용자 식별은 요청 body의 username이 아니라 JWT 인증정보를 기준으로 합니다.

## 가상자산 안전성

송금, 매수/매도, 출석/활동 보상처럼 잔고가 바뀌는 경로는 DB write lock을 사용해
동시 요청에 따른 lost update와 중복 보상을 줄입니다.

공개 버전에서 임의 입금/출금 API는 비활성화되어 있으며,
사용자 간 송금은 로그인 및 계좌 PIN 검증 후 수행됩니다.

## Rate limiting

로그인, 회원가입, 커뮤니티 작성, 이미지 업로드, 송금, 매수/매도와
공개 주가/뉴스 조회에 기본적인 IP 단위 요청 제한을 적용합니다.

현재 제한기는 단일 애플리케이션 인스턴스 메모리 기반입니다.
향후 다중 인스턴스로 확장할 경우 Redis/Valkey 기반 분산 rate limiter로 교체해야 합니다.

## 취약점 제보

보안 문제를 발견한 경우 공개 Issue에 비밀정보나 실제 공격 데이터를 게시하지 마세요.
저장소 소유자에게 비공개 채널로 재현 조건과 영향 범위를 전달해 주세요.


## 브라우저 보안 헤더

운영 프론트는 HSTS, `X-Content-Type-Options`, `X-Frame-Options`,
`Referrer-Policy`, `Permissions-Policy`와 CSP를 적용합니다.

CSP는 기능 호환성을 확인하는 동안 `Content-Security-Policy-Report-Only`로 운영하며,
legacy `report-uri`와 최신 `Reporting-Endpoints` / `report-to`를 함께 사용합니다.
위반 로그를 확인한 뒤 필요한 외부 출처만 허용하고, 정상 운영이 확인된 다음 강제 정책으로 전환합니다.

Vite의 해시 기반 `/assets/*` 파일만 장기 immutable 캐시하고,
백엔드 API 응답은 Spring Security의 `no-store` 정책을 유지해 인증·개인화 데이터가
공유 CDN 캐시에 저장되지 않도록 합니다.
