FROM node:24-alpine AS build

ARG API_URL=http://api.originhub.local
ARG GIT_SSH_URL=git@127.0.0.1:30222

RUN corepack enable && corepack prepare pnpm@10 --activate

WORKDIR /app

COPY originhub-frontend/package.json originhub-frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY originhub-frontend/ ./

RUN printf "VERCEL_API_BASE_URL=%s\nVERCEL_GIT_SSH_URL=%s\n" "${API_URL}" "${GIT_SSH_URL}" > .env \
    && node set-env.js \
    && pnpm run build

FROM nginx:alpine

COPY deploy/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/originhub-frontend/browser /usr/share/nginx/html
