# jari-isseo-server

자리있어의 서버와 손님용 모바일 웹을 담은 레포입니다.
사장님이 앱에서 테이블을 터치하면, 손님은 링크(QR·카톡)로 열린 웹에서 실시간으로 빈자리를 확인합니다.

- 사장님 앱(Android): [jari-isseo-android](https://github.com/jari-isseo/jari-isseo-android)
- 손님 화면: 이 레포의 모바일 웹 (앱 설치·로그인 없음)

## 폴더 구조

    jari-isseo-server/
    ├── src/main/java/            Spring Boot 서버 (REST API, SSE 실시간 전달)
    ├── src/main/resources/
    │   ├── application.yml       서버 설정 (비밀 값은 .env)
    │   └── static/viewer/        손님 웹 (index.html, renderer.js, style.css — 빌드 도구 없음)
    ├── docs/samples/             배치·스냅샷 샘플 JSON (앱·서버·웹 공통 데이터 형식)
    └── deploy/                   배포 설정 (Nginx, 실행 스크립트 등)

> `src/`는 Spring Boot 프로젝트 세팅 이슈(#10)에서 추가됩니다.

## 기술 스택

| 구분 | 사용 |
|---|---|
| 서버 | Spring Boot, Spring Data JPA |
| DB | MySQL |
| 실시간 | SSE (Server-Sent Events) |
| 손님 웹 | HTML · JavaScript · SVG (2D 아이소메트릭) |
| 배포 | AWS Lightsail (서울), Nginx |

## 문서

- 노션 (요구사항 분석서 · API 정의서 · 개발 순서): https://app.notion.com/p/416db762435d8166b74aec1f4f8b1f4d