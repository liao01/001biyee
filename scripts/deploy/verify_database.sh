#!/usr/bin/env bash
set -euo pipefail

docker exec lyw-mysql sh -c 'mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SELECT CONCAT(\"tables=\", COUNT(*)) FROM information_schema.tables WHERE table_schema = DATABASE(); SELECT CONCAT(\"categories=\", COUNT(*)) FROM post_category;"'
docker exec lyw-mysql sh -c 'mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SHOW VARIABLES WHERE Variable_name IN (\"innodb_buffer_pool_size\", \"max_connections\", \"performance_schema\");"'

for container in lyw-mysql lyw-mongo lyw-redis; do
  test -z "$(docker port "$container")"
  echo "$container published_ports=0"
done

if ss -lnt | grep -Eq ':(3306|27017|6379) '; then
  echo "database port exposed on host" >&2
  exit 1
fi
