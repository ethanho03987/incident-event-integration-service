# ---- Stage 1: build the jar with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy the pom first and download dependencies, so Docker caches this layer
# and only re-downloads when pom.xml changes (not on every code change).
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

# ---- Stage 2: small runtime image with just the JRE and the jar ----
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 appuser
WORKDIR /app
COPY --from=build /app/target/incident-service.jar app.jar
USER appuser
EXPOSE 8080
# Use up to 75% of the container's memory limit for the heap
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
