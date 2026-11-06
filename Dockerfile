# ─── Stage 1: Angular Build ───────────────────────────────────────────────
FROM node:24-alpine AS frontend-build

ARG API_URL=http://localhost:8080
ARG GIT_SSH_URL=git@localhost:2222

RUN corepack enable && corepack prepare pnpm@10 --activate

WORKDIR /app

COPY gitnode-frontend/package.json gitnode-frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY gitnode-frontend/ ./

RUN printf "VERCEL_API_BASE_URL=%s\nVERCEL_GIT_SSH_URL=%s\n" "${API_URL}" "${GIT_SSH_URL}" > .env \
    && node set-env.js \
    && pnpm run build

# ─── Stage 2: Maven Build ─────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS backend-build

WORKDIR /app

COPY pom.xml ./
COPY .mvn ./.mvn
COPY gitnode-backend/pom.xml ./gitnode-backend/
COPY gitnode-events/pom.xml ./gitnode-events/
RUN mvn dependency:go-offline -pl gitnode-backend -am -q

COPY --from=frontend-build /app/dist/gitnode-frontend/browser \
     ./gitnode-backend/src/main/resources/static

COPY gitnode-backend/src ./gitnode-backend/src
COPY gitnode-events/src ./gitnode-events/src
RUN mvn package -pl gitnode-backend -am -DskipTests -Derrorprone.skip=true

# ─── Stage 3: Final Image ─────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="GitNode"
LABEL org.opencontainers.image.description="Self-hosted Git hosting platform with enterprise SSO and audit logging"
LABEL org.opencontainers.image.source="https://github.com/nuricanozturk01/gitnode"
LABEL org.opencontainers.image.features="git-hosting,saml-sso,ldap-sso,audit-log"

RUN apk add --no-cache tini postgresql18 redis \
    && addgroup -g 1001 -S forge && adduser -u 1001 -S forge -G forge

WORKDIR /app

COPY --from=backend-build /app/gitnode-backend/target/gitnode-backend.jar app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh

RUN chmod 755 /app/entrypoint.sh \
    && mkdir -p /data/repos /var/log/gitnode \
    && chown -R forge:forge /data /var/log/gitnode

USER forge

# Everything GitNode keeps (repositories, and in zero-config mode the embedded PostgreSQL,
# Redis and generated secrets) lives under /data. Mount a volume here.
VOLUME /data

# The image bundles the Angular SPA; the "os" profile serves it (SpaWebMvcConfig). Override to change.
ENV SPRING_PROFILES_ACTIVE=os

EXPOSE 8080 2222

# Zero-config:  docker run -p 8080:8080 -p 2222:2222 -v gitnode-data:/data <image>
# External DB:  set SPRING_DATASOURCE_URL (and/or SPRING_DATA_REDIS_HOST); the embedded
#               PostgreSQL / Redis are then not started. See docker/entrypoint.sh.
ENTRYPOINT ["/sbin/tini", "--", "/app/entrypoint.sh"]
