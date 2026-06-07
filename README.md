# vpn-lab-migrated

원본: `~/Desktop/vpn-lab/` (Rust + Dioxus + WireGuard PoC)
이 폴더: 사내 보안 솔루션 마이그레이션 산출물

## 목표 스택

| 영역 | 스택 | 버전 |
|---|---|---|
| 에이전트 | Kotlin + GraalVM Native | Kotlin 2.1.x, GraalVM 21 LTS |
| Control Plane | Kotlin + Spring Boot | 3.5.x LTS, JDK 21 LTS |
| 프런트엔드 | Vue 3 + Vite + TypeScript | Vue 3.5+, Vite 6+ |
| DB | MongoDB | 8.0 LTS |
| 빌드 | Gradle Kotlin DSL | 8.x |

## 재현 대상 (원본과 동일 동작 보장)

1. VPN 클라우드 — 5클라이언트, 격리, Kill Switch, DNS leak 차단
2. 웹 차단 — DNS 레벨 도메인 차단 + 와일드카드
3. 디스크 통제 — 감지·로깅 + USB 읽기전용 차단
4. control-plane API — 13~14 엔드포인트 (히스토리 확인)
5. 백오피스 UI — 4화면 (대시보드/블록리스트/이벤트로그/정책설정)
6. 와일드카드 자동 반영 (CoreDNS 자동 재시작)

## 진행 로그

마이그레이션 진행 기록은 원본 폴더의 `~/Desktop/vpn-lab/.MD/MIGRATION_LOG.md` 에 누적.

## 안전 규칙

- 원본 `~/Desktop/vpn-lab/`의 Rust 코드 / `.MD` / `PROJECT_STATUS.html` 절대 수정 금지
- 모든 신규 코드는 이 폴더 안에서 작성
- 각 모듈 (a)구현 → (b)빌드 → (c)검증 → (d)다음모듈 사이클
- 같은 에러 5회 시 해당 모듈 중단
