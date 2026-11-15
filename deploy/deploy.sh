#!/usr/bin/env bash
# 자리있어 서버 재배포 (#25): 내 PC 에서 jar 빌드 → 서버 업로드 → 재시작 → 응답 확인
#
# 사용 (레포 루트, Git Bash 또는 WSL):
#   bash deploy/deploy.sh                # 테스트 + 빌드 + 배포
#   bash deploy/deploy.sh --skip-tests   # 테스트 건너뛰기 (급할 때만)
#
# 안전장치
#   1) 내 PC: 배포 브랜치(develop)가 아니거나, 변경·새 파일이 있거나, push 안 된 커밋이 있으면 멈춤
#      → 빌드하는 코드 = GitHub 의 develop 과 항상 같음
#   2) 서버: 레포에 손으로 고친 파일이 있으면 멈춤 (비밀값 deploy/.env·jar 는 gitignore 라 상관없음)
#   3) 서버 작업(코드 맞추기 ~ 재시작 ~ 되돌리기)은 잠금(flock)으로 한 번에 하나씩만
#      jar 는 배포마다 다른 이름으로 올려서, 동시에 두 명이 배포해도 서로 덮어쓰지 않음
#   4) 재시작 실패 또는 60초 안에 앱(8080)·Nginx(80) 둘 다 응답하지 않으면 직전 jar 로 되돌림
#   5) 중간에 실패해도 업로드 파일은 지우고, 남은 것은 다음 배포 때 정리
set -euo pipefail

cd "$(dirname "$0")/.."

# ---------- 설정 (deploy/deploy.env 가 있으면 그 값 사용) ----------
DEPLOY_HOST=ubuntu@3.34.70.19
DEPLOY_KEY=~/.ssh/LightsailDefaultKey-ap-northeast-2.pem
DEPLOY_DIR=jari-isseo-server
DEPLOY_BRANCH=develop
if [[ -f deploy/deploy.env ]]; then
  # shellcheck disable=SC1091
  source deploy/deploy.env
fi
DEPLOY_KEY="${DEPLOY_KEY/#\~/$HOME}"

SKIP_TESTS=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=true ;;
    *) echo "알 수 없는 옵션: $arg" >&2; exit 1 ;;
  esac
done

step() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31m✖ %s\033[0m\n' "$*" >&2; exit 1; }

SSH=(ssh -i "$DEPLOY_KEY" -o StrictHostKeyChecking=accept-new "$DEPLOY_HOST")
SCP=(scp -i "$DEPLOY_KEY" -o StrictHostKeyChecking=accept-new)

[[ -f "$DEPLOY_KEY" ]] || fail "SSH 키가 없어요: $DEPLOY_KEY (deploy/deploy.env 의 DEPLOY_KEY 확인)"

# ---------- 1. 배포할 코드 확인 ----------
step "1/4 배포할 코드 확인"
branch=$(git rev-parse --abbrev-ref HEAD)
[[ "$branch" == "$DEPLOY_BRANCH" ]] || fail "지금 브랜치가 $branch 예요. git switch $DEPLOY_BRANCH 후 다시 실행하세요"
# 결과를 따로 받아서 확인 (git status 가 실패하면 set -e 로 여기서 멈춤)
#   새 파일(untracked)도 검사: 빌드에 들어가는데 커밋에는 없는 파일이 섞이지 않도록. .env 등은 gitignore 라 제외됨
worktree=$(git status --porcelain)
[[ -z "$worktree" ]] || fail "커밋하지 않은 변경·새 파일이 있어요. 커밋하거나 정리한 뒤 다시 실행하세요:
$worktree"
git fetch --quiet origin "$DEPLOY_BRANCH"
local_sha=$(git rev-parse HEAD)
remote_sha=$(git rev-parse "origin/$DEPLOY_BRANCH")
[[ "$local_sha" == "$remote_sha" ]] || fail "GitHub 의 $DEPLOY_BRANCH 와 달라요. git pull / git push 로 맞춘 뒤 다시 실행하세요"
echo "배포 커밋: $(git log -1 --format='%h %s')"

# ---------- 2. jar 빌드 ----------
step "2/4 jar 빌드 (내 PC)"
if [[ "$SKIP_TESTS" == true ]]; then
  ./gradlew --quiet clean bootJar
else
  ./gradlew --quiet clean test bootJar
fi
jar=$(ls build/libs/*.jar | grep -v -- '-plain\.jar$' | head -n 1)
[[ -n "$jar" ]] || fail "build/libs 에 jar 가 없어요"
echo "빌드 결과: $jar ($(du -h "$jar" | cut -f1))"

# ---------- 3. jar 업로드 (배포마다 다른 이름) ----------
step "3/4 jar 업로드"
upload="deploy/app.jar.${local_sha:0:7}.$(date +%s).$$.new"
# 업로드나 서버 접속이 실패해서 서버 쪽 정리(trap)가 못 돌면, 여기서 업로드 파일을 지워 본다 (안 돼도 무시)
#   그래도 남은 파일은 다음 배포 때 서버가 1시간 넘은 것부터 정리
cleanup_upload() {
  "${SSH[@]}" "rm -f '$DEPLOY_DIR/$upload'" > /dev/null 2>&1 || true
}
trap cleanup_upload EXIT
"${SCP[@]}" "$jar" "$DEPLOY_HOST:$DEPLOY_DIR/$upload"

# ---------- 4. 서버: 잠금 → 코드 맞추기 → 교체 → 재시작 → 확인 (실패하면 되돌리기) ----------
step "4/4 서버 반영 (잠금 → git pull → 재시작 → 응답 확인)"
"${SSH[@]}" "DEPLOY_DIR='$DEPLOY_DIR' DEPLOY_BRANCH='$DEPLOY_BRANCH' EXPECTED_SHA='$local_sha' UPLOAD='$upload' bash -s" <<'REMOTE'
set -euo pipefail
cd "$DEPLOY_DIR"
trap 'rm -f "$UPLOAD"' EXIT   # 어떤 이유로 끝나든 이번 배포의 업로드 파일은 정리

# 한 번에 하나의 배포만 (다른 배포가 진행 중이면 최대 5분 기다림)
exec 9> /tmp/jariisseo-deploy.lock
flock -w 300 9 || { echo "✖ 다른 배포가 진행 중이에요. 잠시 후 다시 실행하세요"; exit 1; }

# 예전 배포가 중간에 끊겨 남은 업로드 파일 정리 (1시간 넘은 것, 이번 업로드 제외)
find deploy -maxdepth 1 -name 'app.jar.*.new' -mmin +60 ! -path "$UPLOAD" -delete || true

# 서버 레포에 손으로 고친 파일이 있으면 멈춤
#   업로드 파일(app.jar.*.new)은 제외: 서버가 아직 #25 이전 커밋이면 gitignore 규칙이 없어서 새 파일로 보이기 때문
#   (deploy/.env 는 원래 gitignore)
server_tree=$(git status --porcelain)
server_tree=$(printf '%s\n' "$server_tree" | grep -vE '^\?\? deploy/app\.jar\.[^/]+\.new$' || true)
if [[ -n "$server_tree" ]]; then
  echo "✖ 서버 레포에 커밋되지 않은 변경이 있어요. 서버에서 확인하세요:"
  echo "$server_tree"
  exit 1
fi

git fetch --quiet origin
git switch --quiet "$DEPLOY_BRANCH"
git pull --quiet --ff-only
[[ "$(git rev-parse HEAD)" == "$EXPECTED_SHA" ]] || { echo "✖ 서버 커밋이 내 PC 와 달라요 (그 사이 develop 이 바뀌었을 수 있어요)"; exit 1; }
echo "서버 커밋 일치: ${EXPECTED_SHA:0:7}"

COMPOSE=(docker compose -f deploy/docker-compose.yml)

healthy() {
  # 없는 매장 조회 → STORE_NOT_FOUND 가 오면 정상
  #   앱 직접(8080)과 손님이 실제로 들어오는 Nginx(80) 둘 다 확인
  local app_ok=false
  for _ in $(seq 1 30); do
    if [[ "$app_ok" == false ]] && curl -s --max-time 2 http://localhost:8080/api/public/stores/nostore1 | grep -q STORE_NOT_FOUND; then
      app_ok=true
      echo "  앱(8080) 응답 확인"
    fi
    if [[ "$app_ok" == true ]] && curl -s --max-time 2 http://localhost/api/public/stores/nostore1 | grep -q STORE_NOT_FOUND; then
      echo "  Nginx(80) 응답 확인"
      return 0
    fi
    sleep 2
  done
  [[ "$app_ok" == true ]] && echo "  ✖ 앱은 응답하지만 Nginx(80)를 거치면 응답이 없어요" || echo "  ✖ 앱(8080)이 응답하지 않아요"
  return 1
}

# 직전 jar 보관 → 이번 jar 로 교체
had_prev=false
if [[ -f deploy/app.jar ]]; then
  cp deploy/app.jar deploy/app.jar.prev
  had_prev=true
fi
mv "$UPLOAD" deploy/app.jar

# compose 실패와 응답 실패 모두 되돌리기로 (if 안이라 set -e 로 중간에 끝나지 않음)
if "${COMPOSE[@]}" up -d --build && healthy; then
  echo "✔ 배포 완료"
  "${COMPOSE[@]}" ps || true
  docker image prune -f > /dev/null || true   # 이전 빌드 이미지 정리 (디스크 절약)
  exit 0
fi

echo "✖ 새 버전이 실행되지 않거나 60초 안에 응답하지 않아요. 최근 로그:"
"${COMPOSE[@]}" logs --tail 40 app || true   # 로그 실패가 되돌리기를 막지 않게
"${COMPOSE[@]}" logs --tail 10 nginx || true

if [[ "$had_prev" == true ]]; then
  echo "↩ 직전 jar 로 되돌립니다"
  mv deploy/app.jar deploy/app.jar.failed
  mv deploy/app.jar.prev deploy/app.jar
  if "${COMPOSE[@]}" up -d --build && healthy; then
    echo "↩ 직전 버전으로 복구됨 (실패한 jar: deploy/app.jar.failed)"
  else
    echo "✖ 직전 버전도 응답하지 않아요. 서버에서 확인 필요"
  fi
else
  echo "✖ 되돌릴 직전 jar 가 없어요 (첫 배포). 서버에서 확인 필요"
fi
exit 1
REMOTE

# 여기까지 오면 서버가 업로드 파일을 이미 정리했으므로 로컬 정리는 생략
trap - EXIT
