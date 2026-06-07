# OriginHub backend only (API + Git SSH) — no embedded SPA.
FROM maven:3.9-eclipse-temurin-25 AS backend-build

WORKDIR /app

COPY pom.xml ./
COPY .mvn ./.mvn
COPY originhub-backend/pom.xml ./originhub-backend/
COPY originhub-events/pom.xml ./originhub-events/
RUN mvn dependency:go-offline -pl originhub-backend -am -q

COPY originhub-backend/src ./originhub-backend/src
COPY originhub-events/src ./originhub-events/src
RUN mvn package -pl originhub-backend -am -DskipTests -Derrorprone.skip=true

FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="OriginHub Backend"
LABEL org.opencontainers.image.description="OriginHub API and Git SSH server"

RUN addgroup -g 1001 -S forge && adduser -u 1001 -S forge -G forge

WORKDIR /app

COPY --from=backend-build /app/originhub-backend/target/originhub-backend.jar app.jar

RUN mkdir -p /data/repos && chown forge:forge /data/repos

USER forge

EXPOSE 8080 2222

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75", \
  "-XX:+OptimizeStringConcat", \
  "-XX:+UseStringDeduplication", \
  "-jar", "app.jar"]
