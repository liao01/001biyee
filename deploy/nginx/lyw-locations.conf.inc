# BEGIN LYW ROUTES
location = /travel { return 301 /travel/; }
location /travel/ {
  set $lyw_frontend_upstream lyw-frontend:80;
  proxy_pass http://$lyw_frontend_upstream;
  include proxy.conf;
}
location = /travel-admin { return 301 /travel-admin/; }
location /travel-admin/ {
  set $lyw_frontend_upstream lyw-frontend:80;
  proxy_pass http://$lyw_frontend_upstream;
  include proxy.conf;
}
location ^~ /business/lyw/actuator/ {
  access_log off;
  return 404;
}
location /business/ {
  set $lyw_backend_upstream lyw-backend:8080;
  rewrite ^/business(/.*)$ $1 break;
  proxy_pass http://$lyw_backend_upstream;
  include proxy.conf;
  client_max_body_size 100m;
}
# END LYW ROUTES
