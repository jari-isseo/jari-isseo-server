#!/usr/bin/env bash
# HTTPS 인증서 처음 발급 (#26): 서버에서 한 번만 실행 (MobaXterm, 레포 루트)
#   bash deploy/init-https.sh            # 발급 + Nginx HTTPS 로 전환
#   bash deploy/init-https.sh --dry-run  # 실제 발급 없이 연습 (Let's Encrypt 발급 횟수 제한을 아끼려면 먼저)
#
# 준비: deploy/.env 에 SERVER_DOMAIN(예: jari.example.com), DNS A 레코드가 이 서버 고정 IP 를 가리킬 것
# 이후 갱신은 certbot 컨테이너가 12시간마다 자동으로 시도 (만료 30일 전부터 실제 갱신)
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f deploy/docker-compose.yml)
env_get() { grep -E "^$1=" deploy/.env | tail -n 1 | cut -d= -f2- || true; }

DOMAIN=$(env_get SERVER_DOMAIN)
EMAIL=$(env_get CERTBOT_EMAIL)
[[ -n "$DOMAIN" ]] || { echo "✖ deploy/.env 에 SERVER_DOMAIN 을 넣어 주세요"; exit 1; }

DRY=()
[[ "${1:-}" == "--dry-run" ]] && DRY=(--dry-run)

echo "▶ 1/4 DNS 확인: $DOMAIN"
dns_ip=$(getent ahostsv4 "$DOMAIN" | awk 'NR==1 {print $1}' || true)
my_ip=$(curl -s --max-time 5 https://checkip.amazonaws.com || true)
echo "  DNS → ${dns_ip:-없음} / 이 서버 → ${my_ip:-확인 실패}"
[[ -n "$dns_ip" ]] || { echo "✖ $DOMAIN 의 A 레코드를 찾을 수 없어요. DNS 설정을 확인하세요"; exit 1; }
if [[ -n "$my_ip" && "$dns_ip" != "$my_ip" ]]; then
  echo "✖ DNS 가 이 서버를 가리키지 않아요. A 레코드 값을 $my_ip 로 해 주세요 (Cloudflare 면 프록시 끄기)"; exit 1
fi

echo "▶ 2/4 Nginx 가 발급용 경로를 받을 수 있게 실행"
"${COMPOSE[@]}" up -d nginx

echo "▶ 3/4 인증서 발급 (Let's Encrypt, webroot)"
if [[ -n "$EMAIL" ]]; then account=(--email "$EMAIL" --no-eff-email); else account=(--register-unsafely-without-email); fi
"${COMPOSE[@]}" run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot -d "$DOMAIN" \
  --agree-tos --non-interactive --keep-until-expiring "${account[@]}" "${DRY[@]}"

if [[ ${#DRY[@]} -gt 0 ]]; then
  echo "✔ 연습 성공. --dry-run 없이 다시 실행하면 실제로 발급해요"; exit 0
fi

echo "▶ 4/4 Nginx 를 HTTPS 로 전환 + 갱신 컨테이너 실행"
"${COMPOSE[@]}" up -d --force-recreate nginx
"${COMPOSE[@]}" up -d certbot
sleep 3
if curl -s --max-time 5 --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/api/public/stores/nostore1" | grep -q STORE_NOT_FOUND; then
  echo "✔ HTTPS 동작 확인: https://$DOMAIN"
  echo "  갱신 연습: ${COMPOSE[*]} run --rm --entrypoint certbot certbot renew --dry-run --webroot -w /var/www/certbot"
else
  echo "✖ HTTPS 응답 확인 실패. 로그: ${COMPOSE[*]} logs --tail 30 nginx"; exit 1
fi
