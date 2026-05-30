FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.description="Forge Backend"

RUN addgroup -g 1001 -S forge && adduser -u 1001 -S forge -G forge
USER forge

ADD target/forge-backend.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom","-XX:MaxRAMPercentage=75", "-jar", "/app.jar", "1>/dev/stdout", "2>/dev/stderr"]
