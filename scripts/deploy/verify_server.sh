#!/usr/bin/env bash
set -euo pipefail

echo "containers"
docker inspect -f '{{.Name}} status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} restart={{.RestartCount}} oom={{.State.OOMKilled}}' lyw-mysql lyw-mongo lyw-redis lyw-backend lyw-frontend lyw-prometheus
echo "http"
for path in / /travel/ /travel/CardList /travel-admin/ /travel-admin/login /business/lyw/web/post/categories; do
  code=$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1$path")
  echo "$path $code"
done
echo "host"
free -m
df -h /
ss -lnt | grep -E ':(3306|27017|6379) ' && exit 1 || true
