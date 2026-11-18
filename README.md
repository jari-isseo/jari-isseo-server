# 자리있어?! · 서버 & 손님 웹

> 가기 전에, 빈자리부터 확인하세요.

**자리있어?!** 는 사장님이 매장을 한 번 스캔해 배치도를 만들고, 테이블을 터치해 빈자리/사용중을 바꾸면
손님이 링크(QR·카카오톡·지도 앱 가게 정보) 하나로 **앱 설치 없이** 실시간 빈자리를 확인하는 서비스입니다.

이 레포는 **서버(Spring Boot)** 와 **손님용 모바일 웹(아이소메트릭 배치도)** 을 담고 있습니다.

| | |
|---|---|
| 운영 주소 | https://jariisseo.mingu.cloud |
| 손님 링크 | `https://jariisseo.mingu.cloud/s/{publicId}` |
| API 문서 (Swagger) | https://jariisseo.mingu.cloud/swagger-ui.html |
| 사장님 앱 (Android) | [jari-isseo-android](https://github.com/jari-isseo/jari-isseo-android) |
| 기획 문서 (노션) | [요구사항 분석서 · API 정의서 · 개발 순서](https://app.notion.com/p/416db762435d8166b74aec1f4f8b1f4d) |

2026 신한대학교 SW중심대학 앱개발 경진대회 출품작 · 팀원: 이민구(사장님 앱), 정세헌(서버 · 손님 웹 · 배포)

## 서비스 흐름

```
[사장님 앱]                                [서버]                          [손님 폰 브라우저]
 가입 → 층 만들기
 벽 스캔 (화면을 벽에 대고 볼륨 버튼)
 테이블 스캔 (YOLOv8, 온디바이스) → 보정 ──▶ 배치 저장 (JSON)
 링크·QR 공유                                                    ──────▶ /s/{publicId} 열기
 테이블 터치 (빈자리 ↔ 사용중)          ──▶ 상태 저장 → 커밋 후 SSE ──────▶ 새로고침 없이 바로 반영
```

## 시스템 구성

```
                    ┌──────────── AWS Lightsail (서울) · Docker Compose ────────────┐
사장님 앱 ─ HTTPS ─┐ │                                                              │
                  ├─┼─▶ Nginx (80·443) ──▶ Spring Boot (:8080) ──▶ MySQL 8.4      │
손님 웹 ── HTTPS ─┘ │     TLS 종료,           REST API · SSE ·       (외부 비공개)   │
       (SSE)        │     SSE 버퍼링 해제      손님 웹 제공                          │
                    │   certbot (Let's Encrypt 인증서 자동 갱신)                    │
                    └──────────────────────────────────────────────────────────────┘
```

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어·런타임 | Java 21 |
| 서버 | Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Spring Security (JWT), Validation |
| DB | MySQL 8.4 (배치는 JSON 컬럼) |
| 실시간 | SSE (Server-Sent Events), 트랜잭션 커밋 후 이벤트 전송 |
| 손님 웹 | HTML · CSS · JavaScript, SVG 아이소메트릭 렌더러 (빌드 도구 없음) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 배포 | AWS Lightsail (서울), Docker Compose, Nginx, Let's Encrypt (certbot) |

## 주요 기능

| 기능 | 설명 | 이슈 |
|---|---|---|
| 아이소메트릭 렌더러 | 바닥·벽·테이블·의자를 SVG 로 그리고, 상태 색·빈자리 요약·정보 오래됨 표시 | #4–#7 |
| 가입·로그인 | 아이디·비밀번호로 가입하면 매장이 함께 만들어짐, JWT 발급 (30일) | #12 |
| 매장 | 내 매장 조회·수정 (이름, 영업 중/마감), 손님 공유 링크 `shareUrl` | #11, #13 |
| 배치 | 층·바닥·벽·테이블·의자를 JSON 한 덩어리로 저장·조회, 저장마다 `version` +1 | #11, #14 |
| 테이블 상태 | 빈자리/사용중 변경 (같은 요청을 다시 보내도 결과가 같음), 전체 비우기 | #15 |
| 손님 스냅샷·페이지 | 로그인 없이 매장·배치·상태를 한 번에 조회, `/s/{publicId}` 페이지 | #16 |
| 실시간 반영 | 상태·배치·매장이 바뀌면 SSE 로 손님 화면에 바로 반영, 끊기면 자동 재연결 | #18–#21 |
| 손님 웹 디자인 | 빈자리 수 카드, 층 칩, 테이블 목록, 영업 종료·배치 준비 중 안내, 공유 | #32, #53 |
| 사장님 모드 | 같은 웹을 사장님 앱 WebView 에서 사용 (테이블 탭 → 앱으로 전달) | #49 |
| 링크 미리보기 | 카카오톡 등에 링크를 붙이면 "{매장 이름} 빈자리" 와 이미지 표시 (Open Graph) | #28 |
| 배포·HTTPS | 명령 한 줄 재배포 (실패 시 되돌림), Let's Encrypt 인증서 자동 갱신 | #23–#26 |

## API

자세한 요청·응답은 Swagger (`/swagger-ui.html`) 와 노션 API 정의서를 참고하세요.

| 구분 | 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| 인증 | POST | `/api/auth/signup` | - | 회원가입 + 매장 생성 |
| 인증 | POST | `/api/auth/login` | - | 로그인 (JWT 발급) |
| 매장 | GET | `/api/owner/store` | JWT | 내 매장 조회 (`shareUrl`, `hasLayout` 포함) |
| 매장 | PATCH | `/api/owner/store` | JWT | 매장 이름·영업 상태 수정 (보낸 필드만) |
| 배치 | GET | `/api/owner/store/layout` | JWT | 내 배치 조회 |
| 배치 | PUT | `/api/owner/store/layout` | JWT | 배치 저장 (통째로, `version` +1) |
| 상태 | PUT | `/api/owner/tables/{tableId}/status` | JWT | 빈자리/사용중 변경 `{ "occupied": true }` |
| 상태 | POST | `/api/owner/tables/status/reset` | JWT | 전체 비우기 |
| 손님 | GET | `/api/public/stores/{publicId}` | - | 손님 스냅샷 (매장 · 배치 · 상태) |
| 손님 | GET | `/api/public/stores/{publicId}/events` | - | 실시간 변경 (SSE) |
| 손님 | GET | `/s/{publicId}` | - | 손님 웹 화면 (+ og 태그) |

### 에러 응답

모든 API 에러는 같은 모양입니다. `message` 는 사용자에게 그대로 보여 줄 수 있는 한국어 문장입니다.

```json
{ "code": "TABLE_NOT_FOUND", "message": "테이블을 찾을 수 없어요" }
```

코드 목록은 `global/error/ErrorCode.java`, 서비스 코드에서는 `throw new ApiException(ErrorCode.TABLE_NOT_FOUND);`

### 실시간 이벤트 (SSE)

`GET /api/public/stores/{publicId}/events`

| 이벤트 | 언제 | 손님 웹 동작 |
|---|---|---|
| `snapshot` | 연결 직후 | 연결마다 처음 한 번 전체를 다시 불러옴 (끊긴 사이 변경 반영) |
| `status` | 테이블 상태 변경·전체 비우기 | 해당 테이블 색·빈자리 수 갱신 |
| `layout` | 배치 저장 | 배치 다시 불러오기 |
| `store` | 매장 이름·영업 상태 변경 | 이름·영업 상태 갱신 |
| `ping` | 20초마다 | 연결 유지 (프록시 타임아웃 방지) |

- 이벤트는 DB **커밋이 끝난 뒤**, 실제로 값이 바뀐 경우에만 보냅니다 (`@TransactionalEventListener(AFTER_COMMIT)`)
- 연결은 30분마다 서버가 닫고 브라우저가 다시 연결합니다. 끊겨 있는 동안 화면에 "연결이 끊겼어요" 를 표시합니다
- 측정: 로컬 서버 → 손님 4–9ms (#21), 운영 환경 휴대폰 LTE 에서 반영 확인 (#27)

## 손님 웹 렌더러

`src/main/resources/static/viewer/` 의 웹 페이지 하나를 3가지 모드로 씁니다.

| 모드 | 쿼리 | 사용처 | 동작 |
|---|---|---|---|
| 손님 | (없음) | 손님 브라우저 | 조회 전용, 공유 버튼 |
| 사장님 | `?mode=owner` | 사장님 앱 WebView | 테이블 탭 → 앱에 전달, 빈자리 수 전달 |
| 출력 | `?mode=export` | 사장님 앱 WebView | 상태 색 없이 그린 PNG 를 앱에 전달 (갤러리 저장) |

- `?floor={floorId}`: 특정 층 (모든 모드). 출력 모드에서 `&floor=all` 이면 모든 층을 한 장에 위아래로

### 앱 브리지 (사장님 앱 ↔ 웹)

| 방향 | 함수 | 언제 |
|---|---|---|
| 웹 → 앱 | `OwnerBridge.onReady()` | 처음 그리기 완료 |
| 웹 → 앱 | `OwnerBridge.onTableTap(tableId, occupied)` | 테이블 탭 (현재 상태) |
| 웹 → 앱 | `OwnerBridge.onSummary(free, total)` | 보고 있는 층의 빈자리 수가 바뀜 |
| 웹 → 앱 | `OwnerBridge.onExport(pngBase64)` | 출력 PNG 완성 |
| 앱 → 웹 | `window.applyStatus(tableId, occupied)` | 상태 변경 API 성공 → 즉시 반영 |
| 앱 → 웹 | `window.revertStatus(tableId)` | API 실패 → 원래대로 |

- 탭한 테이블은 앱 응답(`applyStatus`/`revertStatus`)이 올 때까지 흐리게 표시되고, 같은 테이블은 다시 눌러도 무시됩니다
- 토큰은 웹에 넘기지 않습니다. 상태 변경 API 는 앱이 호출합니다
- 브라우저에는 `OwnerBridge` 가 없으므로 호출 내용이 콘솔에 `[OwnerBridge] ...` 로 찍힙니다

### 샘플로 화면 확인하기 (서버 없이)

1. VS Code 로 **레포 루트**를 열고 Live Server 확장 설치
2. `src/main/resources/static/viewer/index.html` → **Open with Live Server**
3. 주소 뒤에 쿼리를 붙여 확인

| 쿼리 | 내용 |
|---|---|
| `?sample=layout` (기본) | `docs/samples/sample-layout.json` |
| `?sample=nowall` | 벽 없는 버전 (바닥 경계로 벽 대체 확인) |
| `?sample=snapshot` | 배치 + 테이블 상태 |
| `&debug=1` | 좌표 기준점 표시, 탭한 테이블 파란 테두리 + 콘솔 로그 |
| `&now=2026-11-12T11:32:00Z` | 현재 시각 고정 ('정보 오래됨' 확인용, 실제 손님 링크에도 동작) |

예시: 사장님 모드 `?sample=snapshot&mode=owner` → 테이블 탭 후 콘솔에 `applyStatus("t-3f9a", false)` 입력 / 출력 모드 `?sample=snapshot&mode=export&floor=all`

폰에서 보려면 VS Code 설정 `liveServer.settings.host` 를 `0.0.0.0` 으로 바꾸고, 같은 와이파이의 폰에서 `http://{PC IP}:5500/src/main/resources/static/viewer/index.html?sample=snapshot` 를 엽니다 (안 열리면 Windows 방화벽에서 5500 허용).

## 로컬 실행

1. `.env.example` 을 복사해 `.env` 로 만들고 값을 채웁니다 (`.env` 는 커밋 금지)
   - `DB_*`: 로컬 MySQL 접속 정보 / `JWT_SECRET`: 32자 이상 임의 문자열 / `PUBLIC_BASE_URL`: `http://localhost:8080`
2. MySQL 8 실행. Docker 를 쓰면:
   ```bash
   docker compose --env-file .env -f deploy/docker-compose.dev.yml up -d
   ```
3. 서버 실행: IntelliJ ▶ `JariisseoApplication` (또는 `./gradlew bootRun`). **레포 루트**에서 실행해야 합니다
   - dev 프로필은 웹 파일을 `src` 에서 바로 읽으므로, 손님 웹을 고친 뒤 새로고침만 하면 반영됩니다
4. 확인: http://localhost:8080/swagger-ui.html

## 테스트

```bash
./gradlew test          # 로컬 MySQL 이 켜져 있어야 함
```

- 통합·단위 테스트: 인증, 매장, 배치 검증, 상태 전환, 손님 API, SSE 연결·이벤트 전송, og 태그 등
- `http/jariisseo.http`: IntelliJ HTTP Client 로 전체 흐름을 한 번에 실행 (▶▶ Run All Requests in File).
  가입 → 로그인 → 매장 → 배치 → 상태 전환(멱등) → 손님 스냅샷·페이지 → 전체 비우기 → 에러 코드 순서로 실행하고 결과를 검사합니다. 실행할 때마다 새 아이디로 가입하므로 여러 번 돌려도 됩니다

## 배포

서버: AWS Lightsail 서울 (Ubuntu 24.04, 2GB). Docker Compose 로 Nginx · 앱 · MySQL · certbot 을 함께 띄웁니다.

### 재배포 (내 PC 에서 명령 한 줄)

2GB 서버에서는 빌드하지 않습니다. 내 PC 에서 jar 를 만들어 올리고, 서버는 jar 를 이미지에 얹어 재시작만 합니다.

```bash
# Git Bash, 레포 루트, develop 브랜치에서
bash deploy/deploy.sh
```

| 단계 | 하는 일 |
|---|---|
| 1 | develop 이고, 변경·새 파일·push 안 한 커밋이 없는지 확인 (아니면 멈춤) |
| 2 | `./gradlew clean test bootJar` (급할 때 `--skip-tests`) |
| 3 | jar 업로드 (배포마다 다른 이름 → 동시에 배포해도 덮어쓰지 않음) |
| 4 | 서버 잠금 → 서버 레포 변경 확인 → `git pull` → jar 교체 → `docker compose up -d --build` → 앱·Nginx 응답 확인. 실패하면 직전 jar 로 되돌림 |

- 준비: Lightsail SSH 키를 `~/.ssh/LightsailDefaultKey-ap-northeast-2.pem` 에 둡니다. 경로·서버가 다르면 `deploy/deploy.env.example` → `deploy/deploy.env`
- 터미널의 `java` 가 21 이어야 합니다 (`java -version`)

### 서버 처음 준비 (한 번만)

```bash
git clone https://github.com/jari-isseo/jari-isseo-server.git && cd jari-isseo-server
cp deploy/.env.example deploy/.env
nano deploy/.env        # MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET 채우기 (openssl rand -hex 48)
# 그다음 내 PC 에서 bash deploy/deploy.sh

docker compose -f deploy/docker-compose.yml ps            # 상태
docker compose -f deploy/docker-compose.yml logs -f app   # 로그
```

- 인터넷에는 Nginx(80·443)만 열립니다. 앱은 서버 안(`127.0.0.1:8080`), MySQL 은 compose 내부에서만 접속합니다
- SSE 경로는 Nginx 에서 버퍼링을 끄고 연결을 1시간까지 유지합니다 (`deploy/nginx/locations.conf`)
- 비밀값은 서버의 `deploy/.env` 에만 있고, 레포에는 `deploy/.env.example` 만 올립니다
- 메모리 2GB 기준: 앱 `-Xmx512m`, MySQL `innodb_buffer_pool_size=256M`, 스왑 2GB

### HTTPS (한 번만)

1. 도메인 DNS A 레코드가 서버 고정 IP 를 가리키는지 확인 (`nslookup 도메인`)
2. **Lightsail 방화벽에 HTTPS(TCP 443) 열기**: 콘솔 → 인스턴스 → 네트워킹 → IPv4 방화벽 → `HTTPS` 규칙(모든 IP).
   `init-https.sh` 는 서버 안에서 확인하므로 443 이 닫혀 있어도 성공으로 보입니다. 닫힌 채 전환하면 `http://` 링크가 열리지 않는 `https://` 로 이동합니다
3. 서버의 `deploy/.env` 에 `SERVER_DOMAIN`, `PUBLIC_BASE_URL=https://도메인` 을 넣고 실행합니다

```bash
bash deploy/init-https.sh --dry-run   # 연습 (실제 발급 X)
bash deploy/init-https.sh             # 인증서 발급 → Nginx HTTPS 전환
```

- 인증서가 없으면 HTTP(80)로 동작하고, 있으면 80 → 443 으로 이동합니다 (`deploy/nginx/start.sh` 가 인증서 유무로 설정 생성)
- 갱신: `certbot` 컨테이너가 12시간마다 시도 (만료 30일 전부터 실제 갱신), Nginx 는 6시간마다 설정을 다시 읽습니다
- 갱신 연습: `docker compose -f deploy/docker-compose.yml run --rm --entrypoint certbot certbot renew --dry-run --webroot -w /var/www/certbot`

## 폴더 구조

    jari-isseo-server/
    ├── src/main/java/com/jariisseo/
    │   ├── JariisseoApplication.java   서버 시작점
    │   ├── auth/                       가입·로그인, JWT 발급
    │   ├── store/                      매장 (publicId 생성, 내 매장 조회·수정)
    │   ├── layout/                     배치 (JSON 컬럼, 조회·저장, 검증)
    │   ├── status/                     테이블 상태 (복합 키, 상태 전환·전체 비우기)
    │   ├── customer/                   손님 공개 스냅샷 API, /s/{publicId} 페이지 (+ og 태그)
    │   ├── realtime/                   SSE 허브, 커밋 후 이벤트 전송
    │   └── global/
    │       ├── config/                 Security·JWT, Swagger, 스케줄링 등 공통 설정
    │       └── error/                  공통 에러 응답 { code, message }
    ├── src/main/resources/
    │   ├── application.properties      서버 설정 (비밀값은 .env 에서 읽음, dev/prod 프로필)
    │   └── static/viewer/              손님 웹 (손님 브라우저 · 사장님 앱 WebView 공용)
    │       ├── index.html, style.css
    │       ├── renderer.js             아이소메트릭 그리기, 탭 판정, 모드 분기
    │       ├── live.js                 SSE 연결·재연결, 실시간 반영
    │       ├── bridge.js               사장님 앱(WebView) 브리지
    │       ├── export.js               출력 모드 PNG 생성
    │       └── og.png                  링크 미리보기 이미지
    ├── deploy/
    │   ├── docker-compose.yml          운영 (Nginx · 앱 · MySQL · certbot)
    │   ├── docker-compose.dev.yml      로컬 MySQL
    │   ├── Dockerfile                  앱 이미지 (빌드된 jar 를 얹음)
    │   ├── deploy.sh                   재배포 스크립트
    │   ├── init-https.sh               인증서 최초 발급
    │   ├── env-get.sh                  .env 값 읽기 (Compose 와 같은 규칙)
    │   ├── nginx/                      Nginx 설정 (start.sh, locations.conf)
    │   └── .env.example                서버 환경변수 예시
    ├── http/jariisseo.http             API 요청 모음 (전체 흐름 + 에러 코드)
    ├── docs/samples/                   배치·스냅샷 샘플 JSON (앱·서버·웹 공통 데이터 형식)
    ├── .env.example                    로컬 환경변수 예시
    └── build.gradle
