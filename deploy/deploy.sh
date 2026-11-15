#!/usr/bin/env bash
# 자리있어 서버 재배포 (#25): 내 PC 에서 jar 빌드 → 서버 업로드 → 재시작 → 응답 확인
#
# 사용 (레포 루트, Git Bash 또는 WSL):
#   bash deploy/deploy.sh                # 테스트 + 빌드 + 배포
#   bash deploy/deploy.sh --skip-tests   # 테스트 건너뛰기 (급할 때만)
#
# 안전장치
#   1) 커밋 안 한 변경이 있거나, 배포 브랜치(develop)가 아니거나, push 안 된 커밋이 있으면 멈춤
#      → 서버에 올라가는 코드 = GitHub 의 develop 과 항상 같음
#   2) 서버 설정(compose·nginx)도 같은 커밋으로 맞춤 (서버에서 git pull)
#   3) 재시작 후 API 가 응답하는지 확인, 실패하면 직전 jar 로 되돌림
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
step "1/5 배포할 코드 확인"
branch=$(git rev-parse --abbrev-ref HEAD)
[[ "$branch" == "$DEPLOY_BRANCH" ]] || fail "지금 브랜치가 $branch 예요. git switch $DEPLOY_BRANCH 후 다시 실행하세요"
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || fail "커밋하지 않은 변경이 있어요. 커밋하거나 되돌린 뒤 다시 실행하세요"
git fetch --quiet origin "$DEPLOY_BRANCH"
local_sha=$(git rev-parse HEAD)
remote_sha=$(git rev-parse "origin/$DEPLOY_BRANCH")
[[ "$local_sha" == "$remote_sha" ]] || fail "GitHub 의 $DEPLOY_BRANCH 와 달라요. git pull / git push 로 맞춘 뒤 다시 실행하세요"
echo "배포 커밋: $(git log -1 --format='%h %s')"

# ---------- 2. jar 빌드 ----------
step "2/5 jar 빌드 (내 PC)"
if [[ "$SKIP_TESTS" == true ]]; then
  ./gradlew --quiet clean bootJar
else
  ./gradlew --quiet clean test bootJar
fi
jar=$(ls build/libs/*.jar | grep -v -- '-plain\.jar$' | head -n 1)
[[ -n "$jar" ]] || fail "build/libs 에 jar 가 없어요"
echo "빌드 결과: $jar ($(du -h "$jar" | cut -f1))"

# ---------- 3. 서버 설정을 같은 커밋으로 ----------
step "3/5 서버 코드·설정 맞추기 (git pull)"
"${SSH[@]}" "cd $DEPLOY_DIR && git fetch --quiet origin && git switch --quiet $DEPLOY_BRANCH && git pull --quiet --ff-only && git rev-parse HEAD" \
  | tail -n 1 | grep -qx "$local_sha" || fail "서버 커밋이 내 PC 와 달라요. 서버에서 git status 를 확인하세요"
echo "서버 커밋 일치: ${local_sha:0:7}"

# ---------- 4. jar 업로드 ----------
step "4/5 jar 업로드"
"${SCP[@]}" "$jar" "$DEPLOY_HOST:$DEPLOY_DIR/deploy/app.jar.new"

# ---------- 5. 교체 + 재시작 + 확인 (실패하면 되돌리기) ----------
step "5/5 재시작 + 응답 확인"
"${SSH[@]}" "DEPLOY_DIR=$DEPLOY_DIR bash -s" <<'REMOTE'
set -euo pipefail
cd "$DEPLOY_DIR"
COMPOSE=(docker compose -f deploy/docker-compose.yml)

# 직전 jar 보관 → 새 jar 로 교체
[[ -f deploy/app.jar ]] && cp deploy/app.jar deploy/app.jar.prev
mv deploy/app.jar.new deploy/app.jar

healthy() {
  # 없는 매장 조회 → STORE_NOT_FOUND 가 오면 앱·DB 모두 정상
  for _ in $(seq 1 30); do
    if curl -s --max-time 2 http://localhost:8080/api/public/stores/nostore1 | grep -q STORE_NOT_FOUND; then
      return 0
    fi
    sleep 2
  done
  return 1
}

"${COMPOSE[@]}" up -d --build
if healthy; then
  echo "✔ 배포 완료"
  "${COMPOSE[@]}" ps
  docker image prune -f > /dev/null   # 이전 빌드 이미지 정리 (디스크 절약)
  exit 0
fi

echo "✖ 새 버전이 60초 안에 응답하지 않아요. 최근 로그:"
"${COMPOSE[@]}" logs --tail 40 app
if [[ -f deploy/app.jar.prev ]]; then
  echo "↩ 직전 jar 로 되돌립니다"
  mv deploy/app.jar deploy/app.jar.failed
  mv deploy/app.jar.prev deploy/app.jar
  "${COMPOSE[@]}" up -d --build
  healthy && echo "↩ 직전 버전으로 복구됨 (실패한 jar: deploy/app.jar.failed)" || echo "✖ 직전 버전도 응답하지 않아요. 서버에서 확인 필요"
fi
exit 1
REMOTE
