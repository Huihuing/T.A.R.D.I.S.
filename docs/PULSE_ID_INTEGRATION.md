# PULSE//ID · T.A.R.D.I.S. 계정 연결 및 Hub SSO

> **2026-10-28 · feature 브랜치 구현 / 운영 환경 미적용**

T.A.R.D.I.S.에서 원본 `member.id`와 지갑·PIN·거래원장·일일 보상을 보존하면서 PULSE//LAB과 로그인 경로를 연결한다.

## 기존 계정의 1회 연결

- `POST /api/auth/pulse-link/proof`는 기존 Tardis JWT로 로그인한 회원의 실제 member.id에 대해 PULSE challenge/World/iss/aud/jti/iat/exp를 포함한 90초 서명 증명 발급.
- 프론트 `/pulse-link?challenge=...`에서 증명을 복사한다. 비밀번호/PIN을 PULSE 서버로 전달하지 않는다.
- 강력한 `PULSE_LINK_PROOF_SECRET`이 없으면 기능은 503으로 비활성화된다.

## 연결 후 “한 번에 입장” (Hub SSO)

- Portal이 발급하는 단회 60초 티켓을 fragment로 받은 `/pulse-sso`가 즉시 history에서 제거하고 기존 Tardis API `POST /api/auth/pulse-sso`로 전송.
- Tardis 백엔드만 중앙 PULSE `POST /api/pulse-id/sso/redeem`에 **별도 서버 비밀키**로 인증하고 원본 member ID를 가져온다.
- 백엔드는 `memberRepository.findByIdForUpdate`를 통해 회원 잠금을 확보하고 기존 `AuthController.completeLogin` 경로로 **Tardis 고유 JWT/refresh cookie**를 발급한다. 일일 보상 및 PIN 정책을 유지한다.
- 이 방식은 World별 Keycloak OIDC Relying Party가 아니라 중앙 Keycloak 로그인 + 1회용 Hub 티켓이다.

## 환경 설정 (서버 전용)

- `PULSE_LINK_PROOF_SECRET` ↔ 포털 `PULSE_LINK_TARDIS_SECRET`
- `PULSE_SSO_REDEEM_URL=https://<trusted-pulse-host>/api/pulse-id/sso/redeem`
- `PULSE_SSO_SERVICE_KEY` ↔ 포털 `PULSE_SSO_TARDIS_KEY` (proof 서명 키와 반드시 달라야 한다)
- `PULSE_SSO_ALLOW_LOCAL_HTTP=true`는 **로컬 loopback 개발 환경에서만** 허용.
- 기존 JWT/DB/SMTP/Google 비밀키/송금 PIN 시스템은 변경하지 않는다.

## 검증 및 남은 게이트

- CI: [T.A.R.D.I.S. Actions #40648731486](https://github.com/Huihuing/T.A.R.D.I.S./actions/runs/40648731486) Java tests + React build **PASS**.
- Portal 합성 signed proof → 일회용 Hub ticket browser E2E는 별도 중앙 CI에서 PASS.
- 아직 실제 Tardis 테스트 DB/실회원과 중앙 Hub를 함께 실행한 E2E, 계정 정지/로그아웃 즉시 철회, 운영 설정/DB 이사는 미검증. 실제 배포 전 필수.
- **Draft PR #131 유지 / main 미병합 / 운영 배포·환경변수 변경 없음.**

참조: [PULSE//LAB SSO 통합 계약](https://github.com/Huihuing/pulse-lab/blob/feature/pulse-id-foundation/docs/PULSE_HUB_SSO.md).
