# ─── Stage 1: Angular Build ───────────────────────────────────────────────
FROM node:22-alpine AS frontend-build

ARG API_URL=http://localhost:8080
ARG GIT_SSH_URL=git@localhost:2222

RUN corepack enable && corepack prepare pnpm@10 --activate

WORKDIR /app

COPY originhub-frontend/package.json originhub-frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY originhub-frontend/ ./

RUN printf "VERCEL_API_BASE_URL=%s\nVERCEL_GIT_SSH_URL=%s\n" "${API_URL}" "${GIT_SSH_URL}" > .env \
    && node set-env.js \
    && pnpm run build

# ─── Stage 2: Maven Build ─────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS backend-build

WORKDIR /app

COPY pom.xml ./
COPY .mvn ./.mvn
COPY originhub-backend/pom.xml ./originhub-backend/
RUN mvn dependency:go-offline -pl originhub-backend -q

COPY --from=frontend-build /app/dist/originhub-frontend/browser \
     ./originhub-backend/src/main/resources/static

COPY originhub-backend/src ./originhub-backend/src
RUN mvn package -pl originhub-backend -DskipTests -Derrorprone.skip=true

# ─── Stage 3: Final Image ─────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="OriginHub"
LABEL org.opencontainers.image.description="Self-hosted Git hosting platform"
LABEL org.opencontainers.image.source="https://github.com/nuricanozturk01/originhub"

RUN addgroup -g 1001 -S forge && adduser -u 1001 -S forge -G forge

WORKDIR /app

COPY --from=backend-build /app/originhub-backend/target/originhub-backend.jar app.jar

RUN mkdir -p /data/repos && chown forge:forge /data/repos

USER forge

EXPOSE 8080 2222

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "-XX:MaxRAMPercentage=75", \
  "-XX:+OptimizeStringConcat", \
  "-XX:+UseStringDeduplication", \
  "-jar", "app.jar"]
