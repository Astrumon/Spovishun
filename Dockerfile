# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build config first (cacheable layer)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/

# Fix line endings (gradlew may have CRLF on Windows hosts) and warm dependency cache
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code (changes frequently — separate layer)
COPY src/ src/

# Build the distribution (generateVersionInfo runs automatically via compileKotlin)
RUN ./gradlew installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Copy only the distribution from the builder stage
COPY --from=builder /app/build/install/spovishun/ ./

# Run as non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

ENTRYPOINT ["./bin/spovishun"]
