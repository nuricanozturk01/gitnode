FROM node:24-alpine AS build

ARG API_URL=http://api.originhub.test
ENV ORIGINHUB_API_URL=${API_URL}

RUN corepack enable && corepack prepare pnpm@10 --activate

WORKDIR /app

COPY originhub-admin-panel/package.json originhub-admin-panel/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY originhub-admin-panel/ ./

RUN node set-env.js && pnpm exec ng build --configuration production

FROM nginx:alpine

COPY deploy/docker/admin-panel/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/originhub-admin-panel/browser /usr/share/nginx/html
