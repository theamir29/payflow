# syntax=docker/dockerfile:1

# ---- Build: compile and package the executable jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# Dependencies first: this layer is reused until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Run: JRE only, unprivileged user ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/target/payflow.jar app.jar
USER app

ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health" | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
