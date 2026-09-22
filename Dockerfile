# Frontend validation stage
FROM node:24-alpine AS frontend-check
WORKDIR /frontend

COPY Frontend/package.json Frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY Frontend/ .
RUN npm run build

# Backend build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Make the Render build depend on a successful frontend production build.
# The generated files are only a validation artifact and are not shipped
# in the backend runtime image.
COPY --from=frontend-check /frontend/dist /tmp/frontend-build-check

# Copy gradle wrapper and configs
COPY Backend/gradlew .
COPY Backend/gradle gradle
COPY Backend/build.gradle.kts .
COPY Backend/settings.gradle.kts .

# Fix Windows CRLF line endings and grant execute permissions
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code, run backend tests, then build the executable jar
COPY Backend/src src
RUN ./gradlew test bootJar --no-daemon

# Prepare single app.jar ignoring plain jar
RUN cp $(ls /app/build/libs/*.jar | grep -v 'plain') /app/app.jar

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/app.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
