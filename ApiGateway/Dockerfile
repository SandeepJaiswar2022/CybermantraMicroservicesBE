# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# Uses a full JDK image to compile and package the application.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first to leverage Docker layer caching.
# Dependencies are downloaded before copying source code, so re-builds
# triggered by source changes don't re-download all dependencies.
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached layer unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# Uses a slim JRE image — no compiler, no build tools, minimal attack surface.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Create a non-root user to run the application (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the built JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Directory for externally mounted config (public.pem, override YAML)
RUN mkdir -p /app/config && chown -R appuser:appgroup /app

# Directory for log files
RUN mkdir -p /var/log/api-gateway && chown -R appuser:appgroup /var/log/api-gateway

USER appuser

# Expose the gateway port
EXPOSE 8080

# Health check — relies on the actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers:
#   -XX:+UseContainerSupport         Use container CPU/memory limits
#   -XX:MaxRAMPercentage=75.0         Use 75% of container memory for heap
#   -XX:+UseG1GC                     G1 GC is well-suited for microservices
#   -Djava.security.egd=...          Faster entropy for UUID generation
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
    "-jar", "app.jar"]
