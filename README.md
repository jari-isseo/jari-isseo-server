# jari-isseo-server

자리있어의 **서버(Spring Boot)** 와 **손님용 모바일 웹**을 담은 레포입니다.
사장님이 앱에서 테이블을 터치하면, 손님은 링크(QR·카카오톡)로 열린 웹에서 실시간으로 빈자리를 확인합니다.

- 사장님 앱(Android): [jari-isseo-android](https://github.com/jari-isseo/jari-isseo-android)
- 손님 화면: 이 레포의 모바일 웹 (앱 설치·로그인 없음, 사장님 앱은 같은 화면을 WebView로 사용)

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어·런타임 | Java 21 |
| 서버 | Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Spring Security, Validation |
| DB | MySQL 8 |
| API 문서 | springdoc-openapi (Swagger UI) |
| 실시간 | SSE (Server-Sent Events) |
| 손님 웹 | HTML · JavaScript · SVG 2D 아이소메트릭 (빌드 도구 없음) |
| 배포 | AWS Lightsail (서울) |

## 폴더 구조

    jari-isseo-server/
    ├── src/main/java/com/jariisseo/
    │   ├── JariisseoApplication.java   서버 시작점
    │   └── global/
    │       ├── config/                 공통 설정 (Security, Swagger, dev 샘플 경로)
    │       └── error/                  공통 에러 응답 { code, message }
    ├── src/main/resources/
    │   ├── application.properties      서버 설정 (DB 접속 값은 .env 에서 읽음)
    │   └── static/viewer/              웹 렌더러 (손님 웹 · 사장님 앱 WebView 공용)
    │       ├── index.html
    │       ├── renderer.js             아이소메트릭 그리기, 탭 판정, mode 분기
    │       ├── bridge.js               사장님 앱(WebView) 브리지
    │       ├── export.js               출력 모드 PNG 생성
    │       └── style.css
    ├── deploy/docker-compose.dev.yml   로컬 MySQL (Docker 쓰는 경우)
    ├── docs/samples/                   배치·스냅샷 샘플 JSON (앱·서버·웹 공통 데이터 형식)
    ├── .env.example                    로컬 환경변수 예시 (복사해서 .env 로 사용)
    └── build.gradle

## 웹 렌더러

매장 배치도를 SVG 2D 아이소메트릭으로 그리는 웹 페이지 1개를 3가지 모드로 씁니다.

| 모드 | 쿼리 | 사용처 | 동작 |
|---|---|---|---|
| 손님 | (없음) | 손님 브라우저 | 조회 전용 |
| 사장님 | `?mode=owner` | 사장님 앱 WebView | 테이블 탭 → 앱에 전달 |
| 출력 | `?mode=export` | 사장님 앱 WebView | 상태 색 없이 그린 PNG를 앱에 전달 |

- `?floor={floorId}`: 특정 층 (모든 모드). 출력 모드에서 `&floor=all`이면 모든 층을 한 장에 위아래로

### 앱 브리지 (사장님 앱 ↔ 웹)

| 방향 | 함수 | 언제 |
|---|---|---|
| 웹 → 앱 | `OwnerBridge.onReady()` | 처음 그리기 완료 |
| 웹 → 앱 | `OwnerBridge.onTableTap(tableId, occupied)` | 테이블 탭 (현재 상태) |
| 웹 → 앱 | `OwnerBridge.onExport(pngBase64)` | 출력 PNG 완성 |
| 앱 → 웹 | `window.applyStatus(tableId, occupied)` | 상태 변경 API 성공 → 즉시 반영 |
| 앱 → 웹 | `window.revertStatus(tableId)` | API 실패 → 원래대로 |

- 탭한 테이블은 앱 응답(`applyStatus`/`revertStatus`)이 올 때까지 흐리게 표시되고, 같은 테이블은 다시 눌러도 무시됩니다.
- 토큰은 웹에 넘기지 않습니다. 상태 변경 API는 앱이 호출합니다.
- 브라우저에는 `OwnerBridge`가 없으므로 호출 내용이 콘솔에 `[OwnerBridge] ...`로 찍힙니다.

### 로컬에서 확인하기

서버 연동 전에는 샘플 JSON으로 화면을 확인합니다.

1. VS Code로 **레포 루트**를 열고 Live Server 확장 설치
2. `src/main/resources/static/viewer/index.html` → **Open with Live Server**
3. 주소 뒤에 쿼리를 붙여 확인

| 쿼리 | 내용 |
|---|---|
| `?sample=layout` (기본) | `docs/samples/sample-layout.json` |
| `?sample=nowall` | 벽 없는 버전 (바닥 경계로 벽 대체 확인) |
| `?sample=snapshot` | 배치 + 테이블 상태 |
| `&debug=1` | 좌표 기준점 표시, 탭한 테이블 파란 테두리 + 콘솔 로그 |
| `&now=2026-11-12T11:32:00Z` | 현재 시각 고정 ('정보 오래됨' 확인용) |

예시
- 사장님 모드: `?sample=snapshot&mode=owner` → 테이블 탭 후 콘솔에 `applyStatus("t-3f9a", false)` 입력
- 출력 모드: `?sample=snapshot&mode=export&floor=all`

### 폰에서 확인하기

1. VS Code 설정 `liveServer.settings.host`를 `0.0.0.0`으로 변경
2. PC의 IP 확인 (`ipconfig` → IPv4 주소)
3. PC와 **같은 와이파이**에 연결된 폰에서 `http://{PC IP}:5500/src/main/resources/static/viewer/index.html?sample=snapshot`
4. 안 열리면 Windows 방화벽에서 5500 포트 허용

## 에러 응답

모든 API 에러는 같은 모양으로 응답합니다 (API 정의서 7장).

```json
{ "code": "TABLE_NOT_FOUND", "message": "테이블을 찾을 수 없어요" }
```

- 서비스 코드에서: `throw new ApiException(ErrorCode.TABLE_NOT_FOUND);`
- 코드 목록: `global/error/ErrorCode.java`
- `message`는 사용자에게 그대로 보여줄 수 있는 한국어 문장

## 문서

- 노션 (요구사항 분석서 · API 정의서 · 개발 순서): https://app.notion.com/p/416db762435d8166b74aec1f4f8b1f4d