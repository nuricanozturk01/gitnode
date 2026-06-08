# ─── Stage 1: Maven Build (no SPA) ────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS backend-build

WORKDIR /app

COPY pom.xml ./
COPY .mvn ./.mvn
COPY gitnode-backend/pom.xml ./gitnode-backend/
COPY gitnode-events/pom.xml ./gitnode-events/
RUN mvn dependency:go-offline -pl gitnode-backend -am -q

COPY gitnode-backend/src ./gitnode-backend/src
COPY gitnode-events/src ./gitnode-events/src
RUN mvn package -pl gitnode-backend -am -DskipTests -Derrorprone.skip=true

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="GitNode Backend"
LABEL org.opencontainers.image.description="GitNode API backend (no embedded SPA)"

RUN addgroup -g 1001 -S gitnode && adduser -u 1001 -S gitnode -G gitnode

WORKDIR /app

COPY --from=backend-build /app/gitnode-backend/target/gitnode-backend.jar app.jar

RUN mkdir -p /data/repos && chown gitnode:gitnode /data/repos

USER gitnode

EXPOSE 8080 2222

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75", \
  "-XX:+OptimizeStringConcat", \
  "-XX:+UseStringDeduplication", \
  "-jar", "app.jar"]
