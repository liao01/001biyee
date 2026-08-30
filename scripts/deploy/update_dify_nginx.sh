#!/usr/bin/env bash
set -euo pipefail

action=${1:-}
template=/opt/dify/nginx/conf.d/default.conf.template
generated=/opt/dify/nginx/conf.d/default.conf
snippet=/opt/lyw/current/deploy/nginx/lyw-locations.conf.inc
backup_root=/opt/lyw/backups
nginx_container=dify-nginx-1

install_routes() {
  test -f "$snippet"
  if grep -q 'BEGIN LYW ROUTES' "$template" || grep -q 'BEGIN LYW ROUTES' "$generated"; then
    echo "LYW routes already installed" >&2
    exit 1
  fi
  backup_id=$(date -u +%Y%m%dT%H%M%SZ)
  backup_dir="$backup_root/$backup_id"
  install -d -m 700 "$backup_dir"
  cp -- "$template" "$backup_dir/default.conf.template"
  cp -- "$generated" "$backup_dir/default.conf"
  sha256sum "$backup_dir/default.conf.template" "$backup_dir/default.conf" > "$backup_dir/SHA256SUMS"

  python3 - "$template" "$generated" "$snippet" <<'PY'
from pathlib import Path
import sys

snippet = Path(sys.argv[3]).read_text(encoding="utf-8").rstrip() + "\n\n"
for name in sys.argv[1:3]:
    path = Path(name)
    text = path.read_text(encoding="utf-8")
    marker = "    location / {"
    if marker not in text:
        raise SystemExit(f"root location not found: {path}")
    path.write_text(text.replace(marker, snippet + marker, 1), encoding="utf-8")
PY

  if ! docker exec "$nginx_container" nginx -t; then
    cp -- "$backup_dir/default.conf.template" "$template"
    cp -- "$backup_dir/default.conf" "$generated"
    docker exec "$nginx_container" nginx -t
    echo "nginx validation failed; restored $backup_id" >&2
    exit 1
  fi
  docker exec "$nginx_container" nginx -s reload
  printf '%s\n' "$backup_id" > "$backup_root/LAST_BACKUP"
  echo "installed backup_id=$backup_id"
}

rollback_routes() {
  backup_id=${2:-}
  test -n "$backup_id"
  backup_dir="$backup_root/$backup_id"
  test -f "$backup_dir/default.conf.template"
  test -f "$backup_dir/default.conf"
  cp -- "$backup_dir/default.conf.template" "$template"
  cp -- "$backup_dir/default.conf" "$generated"
  docker exec "$nginx_container" nginx -t
  docker exec "$nginx_container" nginx -s reload
  echo "restored backup_id=$backup_id"
}

case "$action" in
  install) install_routes ;;
  rollback) rollback_routes "$@" ;;
  *) echo "usage: $0 install | rollback BACKUP_ID" >&2; exit 2 ;;
esac
