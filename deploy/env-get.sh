# deploy/.env 값 읽기 (#26): init-https.sh, deploy.sh 가 함께 쓴다
#   Compose 와 같은 방식으로 읽는다: 앞뒤 공백, 따옴표("…", '…'), 따옴표 없는 값의 " #주석", 윈도우 줄끝(\r) 제거
#   같은 키가 여러 번 있으면 마지막 값
env_get() {
  local key=$1 file=${2:-deploy/.env} line v
  line=$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}[[:space:]]*=" "$file" 2>/dev/null | tail -n 1) || true
  [[ -n "$line" ]] || return 0
  v=${line#*=}
  v=${v%$'\r'}
  v=${v#"${v%%[![:space:]]*}"}            # 앞 공백
  if [[ "$v" == \"* ]]; then
    v=${v#\"}; v=${v%%\"*}
  elif [[ "$v" == \'* ]]; then
    v=${v#\'}; v=${v%%\'*}
  else
    [[ "$v" == \#* ]] && v=""
    v=${v%%[[:space:]]#*}                  # " #주석"
    v=${v%"${v##*[![:space:]]}"}           # 뒤 공백
  fi
  printf '%s' "$v"
}
