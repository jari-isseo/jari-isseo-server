#!/bin/sh
# Nginx 시작 스크립트 (#26): 인증서가 있으면 HTTPS, 없으면 HTTP 로 설정을 만들고 Nginx 를 띄운다
#   - 인증서 받기 전(처음 배포): 80 에서 그대로 앱으로 넘김 + 인증서 발급용 경로(/.well-known/acme-challenge/)
#   - 인증서 받은 뒤:          80 → 443 으로 이동(발급·갱신 경로만 예외), 443 에서 앱으로 넘김
#   - 6시간마다 설정을 다시 읽음 → certbot 이 갱신한 인증서를 재시작 없이 사용
set -eu

DOMAIN="${SERVER_DOMAIN:-}"
CERT_DIR="/etc/letsencrypt/live/$DOMAIN"
CONF=/etc/nginx/conf.d/default.conf

acme='    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
        default_type "text/plain";
    }'

if [ -n "$DOMAIN" ] && [ -f "$CERT_DIR/fullchain.pem" ] && [ -f "$CERT_DIR/privkey.pem" ]; then
  echo "[jari] 인증서 있음 → HTTPS ($DOMAIN)"
  cat > "$CONF" <<CONF
server {
    listen 80;
    listen [::]:80;
    server_name _;
$acme
    location / {
        return 301 https://$DOMAIN\$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    http2 on;
    server_name $DOMAIN;

    ssl_certificate     $CERT_DIR/fullchain.pem;
    ssl_certificate_key $CERT_DIR/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    include /opt/jari/locations.conf;
}
CONF
else
  echo "[jari] 인증서 없음 → HTTP 만 (도메인: ${DOMAIN:-미설정})"
  cat > "$CONF" <<CONF
server {
    listen 80;
    listen [::]:80;
    server_name _;
$acme
    include /opt/jari/locations.conf;
}
CONF
fi

# 6시간마다 설정 다시 읽기 (갱신된 인증서 반영)
( while :; do sleep 21600; nginx -s reload; done ) &

exec /docker-entrypoint.sh nginx -g 'daemon off;'
