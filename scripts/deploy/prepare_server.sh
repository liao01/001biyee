#!/usr/bin/env bash
set -euo pipefail

test "$(readlink -f /opt/lyw)" = /opt/lyw || { echo "unexpected /opt/lyw target" >&2; exit 1; }
docker network inspect dify_default >/dev/null
test -f /opt/dify/nginx/conf.d/default.conf.template
test -f /opt/dify/nginx/conf.d/default.conf
available_kb=$(df -Pk / | awk 'NR==2 {print $4}')
test "$available_kb" -ge 8388608 || { echo "less than 8GB disk available" >&2; exit 1; }

install -d -o root -g root -m 755 /opt/lyw /opt/lyw/releases /opt/lyw/backups
install -d -o root -g root -m 700 /opt/lyw/secrets
install -d -o root -g root -m 755 /opt/lyw/data/mysql /opt/lyw/data/mongo /opt/lyw/data/redis /opt/lyw/data/uploads
echo "server directories ready"
