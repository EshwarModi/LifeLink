# ===================================================================
# Stage 1: Build the LifeLink Application using Maven & JDK 21
# ===================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies by copying pom.xml first
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy project source code and compile production JAR artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===================================================================
# Stage 2: Minimal Production Runtime Image using JRE 21
# ===================================================================
FROM eclipse-temurin:21-jre-alpine

# Install curl for container health check probes
RUN apk add --no-cache curl

# Security best practice: Run as an unprivileged non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder /build/target/lifelink-*.jar app.jar

# Set ownership of the application binary
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# Container healthcheck querying Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Launch with container-aware JVM memory settings
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
