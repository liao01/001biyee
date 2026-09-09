#!/bin/sh
set -eu

escape_json() {
  printf '%s' "$1" | tr '\r\n' '  ' | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

amap_js_key="$(escape_json "${AMAP_JS_KEY:-}")"
printf 'window.__LYW_RUNTIME_CONFIG__ = {"amapJsKey":"%s"};\n' "$amap_js_key" \
  > /usr/share/nginx/html/travel/runtime-config.js
