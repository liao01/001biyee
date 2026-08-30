FROM nginx:1.27.5-alpine

COPY deploy/frontend/default.conf /etc/nginx/conf.d/default.conf
COPY web-dist/ /usr/share/nginx/html/travel/
COPY admin-dist/ /usr/share/nginx/html/travel-admin/
