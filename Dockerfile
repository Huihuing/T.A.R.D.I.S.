# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy gradle wrapper and configs
COPY Backend/gradlew .
COPY Backend/gradle gradle
COPY Backend/build.gradle.kts .
COPY Backend/settings.gradle.kts .

# Fix Windows CRLF line endings and grant execute permissions
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build jar without tests
COPY Backend/src src
RUN ./gradlew bootJar -x test --no-daemon

# Prepare single app.jar ignoring plain jar
RUN cp $(ls /app/build/libs/*.jar | grep -v 'plain') /app/app.jar

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/app.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
