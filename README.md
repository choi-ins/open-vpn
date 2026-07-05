# vpn-lab-migrated

원본: `~/Desktop/vpn-lab/` (Rust + Dioxus + WireGuard PoC)  
이 폴더: 사내 보안 솔루션 Rust → Kotlin/Spring Boot 3.5 + Vue 3 + MongoDB 8 마이그레이션 산출물

## 스택

| 영역 | 스택 | 버전 |
|---|---|---|
| control-plane | Kotlin + Spring Boot | 3.5.x LTS, JDK 21 LTS |
| agent | Kotlin + GraalVM Native | Kotlin 2.1.x, GraalVM 21 LTS |
| admin-ui | Vue 3 + Vite + TypeScript | Vue 3.5+, Vite 6+ |
| DB | MongoDB | 8.0 LTS |
| 빌드 | Gradle Kotlin DSL | 8.x |

## 아키텍처 (텍스트 다이어그램)

```
┌─────────────────────────────────────────────────────┐
│  admin-ui (Vue 3 / Vite)                            │
│  브라우저 → /api/v1/* → control-plane               │
└────────────────────┬────────────────────────────────┘
                     │ HTTP
┌────────────────────▼────────────────────────────────┐
│  control-plane (Spring Boot 3.5 / JDK 21)           │
│  ├── ClientController   /api/v1/clients/*           │
│  ├── BlocklistController /api/v1/blocklist/*        │
│  ├── DiskControlController /api/v1/disk-control/*  │
│  └── HealthController  /healthz, /actuator/health  │
│                                                     │
│  WireGuardService ──→ connect/disconnect 셸 스크립트 │
│  BlocklistService ──→ MongoDB + CoreDNS 파일 재생성  │
│  DiskControlService ─→ policy.json + pgrep          │
└────────┬──────────────────────┬───────────────────-─┘
         │                      │
┌────────▼───────┐    ┌─────────▼──────────────────────┐
│ MongoDB 8.0    │    │ WireGuard VPN 클라이언트 1-5    │
│ (blocklist DB) │    │ client-{1..5} Docker 컨테이너   │
└────────────────┘    └────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  agent (Kotlin 데몬, GraalVM Native 빌드 가능)       │
│  - FSEvents 기반 디스크 접근 감지                     │
│  - policy.json 읽어 block / readonly / log-only 적용 │
│  - disk-events.jsonl 로그 기록                       │
└─────────────────────────────────────────────────────┘
```

## API 목록 (control-plane)

### VPN 클라이언트 — `/api/v1/clients`

| 메서드 | 경로 | 설명 |
|-------|------|------|
| GET | `/api/v1/clients` | 전 클라이언트 상태 조회 (캐시 TTL 1.5s) |
| GET | `/api/v1/clients/{n}/status` | 단일 클라이언트 WireGuard 상태 |
| POST | `/api/v1/clients/{n}/connect` | VPN 연결 (셸 스크립트 위임) |
| POST | `/api/v1/clients/{n}/disconnect` | VPN 해제 |
| POST | `/api/v1/clients/{n}/killswitch` | Kill Switch 활성화 |
| POST | `/api/v1/clients/{n}/killswitch-off` | Kill Switch 비활성화 |

> `n` 유효 범위: 1 ~ 5 (스크립트 지원 범위 초과 시 400 반환)

### 블록리스트 — `/api/v1/blocklist`

| 메서드 | 경로 | 설명 |
|-------|------|------|
| GET | `/api/v1/blocklist/domains` | 차단 도메인 목록 |
| POST | `/api/v1/blocklist/domains` | 도메인 추가 (`{"domain":"..."}`) |
| DELETE | `/api/v1/blocklist/domains/{domain}` | 도메인 삭제 |
| POST | `/api/v1/blocklist/reload` | CoreDNS 재시작 트리거 |

### 디스크 통제 — `/api/v1/disk-control`

| 메서드 | 경로 | 설명 |
|-------|------|------|
| GET | `/api/v1/disk-control/status` | agent 실행 여부 + PID |
| GET | `/api/v1/disk-control/policy` | 현재 policy.json 내용 |
| PUT | `/api/v1/disk-control/policy` | 모드 변경 (`log-only` / `block` / `readonly`) |
| GET | `/api/v1/disk-control/events` | 디스크 이벤트 로그 (최신 N개, `?limit=10`) |

### 헬스 — 인프라용

| 메서드 | 경로 | 설명 |
|-------|------|------|
| GET | `/healthz` | `{"status":"ok"}` |
| GET | `/actuator/health` | Spring Actuator (MongoDB 포함) |

## 모듈별 실행법

### control-plane

```bash
# 전제: MongoDB 8 실행 중, JDK 21 설치
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# 테스트
cd control-plane
gradle test

# 서버 실행 (기본 포트 8080)
gradle bootRun
```

application.yml 주요 설정:
```yaml
vpn-control:
  scripts-dir: /path/to/scripts   # connect.sh 등 위치
  client-count: 5                 # WireGuard 클라이언트 수 (스크립트 지원 최대 5)
  cache-ttl-ms: 1500              # /clients /status 캐시 TTL
```

### agent

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# 테스트 (Docker 불필요)
cd agent
gradle test

# 실행 (macOS FSEvents 필요, sudo 권한)
gradle run
# 또는 GraalVM Native 빌드
gradle nativeCompile
./build/native/nativeCompile/agent
```

### admin-ui

```bash
cd admin-ui

# 개발 서버 (Vite HMR, 기본 http://localhost:5173)
npm install
npm run dev

# 프로덕션 빌드 → dist/
npm run build

# 단위 테스트
npm test
```

## 재현 대상 (원본 Rust와 동일 동작)

1. VPN 클라이언트 5개 — 격리, Kill Switch, DNS leak 차단
2. 웹 차단 — DNS 레벨 도메인 차단 + 와일드카드 (CoreDNS 자동 재시작)
3. 디스크 통제 — FSEvents 감지 + USB 읽기전용 차단
4. control-plane API 14개 엔드포인트
5. 백오피스 UI 4화면 (대시보드 / 블록리스트 / 이벤트로그 / 정책설정)

## 안전 규칙

- 원본 `~/Desktop/vpn-lab/`의 Rust 코드 / `.MD` / `PROJECT_STATUS.html` 수정 금지
- Docker 의존 작업 (WireGuard 컨테이너, 100개 더미 부하 테스트) 은 로컬에서만
- 각 모듈 (a)구현 → (b)빌드 → (c)검증 → (d)다음모듈 사이클
