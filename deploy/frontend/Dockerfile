FROM nginx:1.27.5-alpine

COPY deploy/frontend/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=755 deploy/frontend/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY web-dist/ /usr/share/nginx/html/travel/
COPY admin-dist/ /usr/share/nginx/html/travel-admin/
