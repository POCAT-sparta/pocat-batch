FROM amazoncorretto:17-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN chown -R appuser:appgroup /app

USER appuser

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dspring.profiles.active=prod", \
  "-jar", \
  "/app/app.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1