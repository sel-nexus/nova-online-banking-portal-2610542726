FROM node:22.12.0-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN node node_modules/@angular/cli/bin/ng.js build
FROM nginx:1.27.3-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/nova/browser /usr/share/nginx/html
EXPOSE 80
