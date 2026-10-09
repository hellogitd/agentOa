FROM public.ecr.aws/docker/library/nginx:1.28.0-alpine@sha256:30f1c0d78e0ad60901648be663a710bdadf19e4c10ac6782c235200619158284
COPY deploy/h5.nginx.conf /etc/nginx/conf.d/default.conf
COPY agentoa-uniapp/dist/build/h5/ /usr/share/nginx/html/
EXPOSE 8080
