# Stage 1: Build application
FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy POM and download dependencies for cache layer
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build jar
COPY src src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /workspace/target/docsearch-service-1.0.0.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:MaxRAMPercentage=70.0", "-jar", "app.jar"]
