FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.description="GitNode Backend"

RUN addgroup -g 1001 -S gitnode && adduser -u 1001 -S gitnode -G gitnode
USER gitnode

ADD target/gitnode-backend.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom","-XX:MaxRAMPercentage=75", "-jar", "/app.jar", "1>/dev/stdout", "2>/dev/stderr"]
