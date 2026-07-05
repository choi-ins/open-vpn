# 자동 재개 작업 지시서 (원격 클라우드 에이전트용)

이 문서는 예약된 클라우드 에이전트가 **콜드 스타트**로 읽는 작업 지시서다.
로컬 세션의 맥락이 없으므로, 여기 적힌 내용만으로 작업을 이어간다.

## 배경

- 이 리포지토리(`choi-ins/open-vpn`, 브랜치 `vpn-lab-migrated`)는
  사내 보안 솔루션 PoC를 Rust → Kotlin/Spring/Vue/MongoDB 로 마이그레이션한 결과물이다.
- 원본(Rust) 코드는 별도 로컬 폴더에 있으며 이 리포지토리에는 없다.
- 진행 이력의 상세 기록은 원본 로컬의 `.MD/MIGRATION_LOG.md`에 있으나,
  클라우드 환경에는 없으므로 아래 "완료 상태"를 기준으로 판단한다.

## 완료 상태 (2026-07-05 시점)

| 모듈 | 상태 |
|---|---|
| control-plane (Kotlin + Spring Boot 3.5) | ✅ API 14개, 테스트 64/64 통과 |
| agent (Kotlin 디스크 통제 데몬) | ✅ 테스트 18개, GraalVM Native 설정됨 |
| admin-ui (Vue 3 + Vite) | ✅ 4화면, 테스트 3개 |
| MongoDB 8 인증 강화 | ✅ 앱 전용 계정 분리 |
| 개선 5종 + 캐싱 레이어 | ✅ 커밋 완료 |

## 클라우드에서 실행 **불가능**한 것 (로컬 Docker 의존)

- WireGuard VPN 컨테이너 기반 부하 테스트
- 100개 더미 컨테이너 소크 테스트
- 실제 docker exec / nslookup 기반 검증
→ 이것들은 로컬 세션에서만 가능하므로 클라우드 에이전트는 시도하지 않는다.

## 클라우드에서 **가능한** 작업 (코드/문서, Docker 불필요)

우선순위 순으로 진행하되, 각 작업 후 커밋한다. 빌드 검증은 실제 실행 출력으로만 판단한다.

1. **control-plane 단위 테스트 실행** — `cd control-plane && ./gradlew test`
   (Testcontainers 필요한 통합 테스트는 Docker 없으면 스킵/실패할 수 있음 — 순수 단위 테스트 위주로 확인)
2. **agent 테스트 실행** — `cd agent && ./gradlew test` (Docker 불필요, 전부 통과해야 함)
3. **admin-ui 빌드 + 단위 테스트** — `cd admin-ui && npm install && npm run build && npm test`
4. **코드 품질 개선** — 컴파일 경고 정리, 사용 안 하는 import 제거, KDoc 보강
5. **README 정비** — 각 모듈 실행법, 아키텍처 다이어그램(텍스트), API 목록 문서화
6. **개선 6번(경계 버그) 수정** — CLIENT_COUNT와 셸 스크립트 지원 범위(1-5) 불일치:
   n이 스크립트 지원 범위를 벗어나면 조용한 실패(HTTP 200 + ok:false) 대신
   명확한 400을 반환하도록. 관련 파일: control-plane WireGuardService / ClientController.

## 진행 규칙

- 파괴적 명령(rm -rf, force push 등) 금지.
- 새 커밋은 `vpn-lab-migrated` 브랜치에 push.
- 비밀정보(.env, 실제 자격증명)를 커밋하지 말 것. `.env.example`의 플레이스홀더만 유지.
- 막히면 그 지점을 이 파일 하단에 기록하고 멈춘다.

## 진행 로그 (에이전트가 append)

### 2026-07-05T18:43 KST — 자동 재개 실행 결과

**1. agent 테스트**
- `gradle test --rerun-tasks` (JAVA_HOME=/opt/homebrew/opt/openjdk@21)
- 결과: **18/18 통과** (BuildEvent 5, EventSerde 3, Logger 2, Policy 4, ReadonlyGuard 4)
- 환경: gradlew 없어 시스템 gradle 사용 (`/opt/homebrew/bin/gradle`)

**2. admin-ui 빌드 + 테스트**
- `npm install && npm run build`: **성공** (Vite 969ms, dist/ 생성)
- `npm test` (vitest run): **3/3 통과** (StatusCard 2, Sidebar 1)
- 취약점 7개(npm audit) — breaking changes 포함이라 `npm audit fix --force`는 미실행

**3. 개선 6번 경계 버그 수정** ✅
- `WireGuardService.SCRIPT_MAX_CLIENT = 5` 상수 도입
- `isValidClientId(n)` → `n in 1..minOf(props.clientCount, SCRIPT_MAX_CLIENT)`
- `ClientController` 에러 메시지를 상수 참조로 변경
- `WireGuardServiceTest` 경계 테스트 3개 추가:
  - `isValidClientId rejects n above SCRIPT_MAX_CLIENT even when clientCount is higher` ✅
  - `isValidClientId rejects n below 1` ✅
  - `isValidClientId accepts valid range 1 to SCRIPT_MAX_CLIENT` ✅
- control-plane 전체 테스트: **67개 전부 통과** (아래 후속 수정 반영)

**4. 코드 품질 개선** ✅
- `ClientController`: 미사용 `CommandResult` import 제거
- `DiskControlController`: 미사용 `JsonNode` import 제거
- 컴파일 (`gradle compileKotlin`) 경고 없이 통과

**5. README 정비** ✅
- 텍스트 아키텍처 다이어그램 추가
- API 14개 목록 (VPN 6개, 블록리스트 4개, 디스크 4개) 문서화
- 각 모듈 실행법 (control-plane / agent / admin-ui)

**커밋**: `db2c54e` — `vpn-lab-migrated` 브랜치에 push 완료

### 2026-07-05T09:42 KST — HealthIntegrationTest 실패 후속 수정

앞선 실행이 "환경 문제"로 넘긴 `HealthIntegrationTest.actuator health endpoint is exposed`(503)를
실제로 진단·수정. 원인은 로컬 MongoDB 미실행이 아니라, 개선 5(Mongo 인증 강화) 이후
로컬 Mongo가 무인증 더미 테스트 URI를 거부 → actuator 집계에 포함된 mongo 헬스가 DOWN → 503.
- 조치: 테스트 프로파일(`src/test/resources/application.yml`)에서만
  `management.health.mongo.enabled=false`. 프로덕션 mongo 헬스 체크는 유지.
- 결과: control-plane **67/67 전부 통과** 확인 (실제 gradle test 출력).
- 커밋: `e2b3fda` — push 완료.
