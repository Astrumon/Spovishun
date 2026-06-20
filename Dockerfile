# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build config first (cacheable layer)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/
COPY config/ config/

# Fix line endings (gradlew may have CRLF on Windows hosts) and warm dependency cache.
# The root project has no dependencies after the multi-module split — resolve :app's instead.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew :app:dependencies --no-daemon

# Copy module sources (changes frequently — separate layer)
COPY common/ common/
COPY domain/ domain/
COPY data/ data/
COPY bot/ bot/
COPY app/ app/

# Build the :app distribution (generateVersionInfo runs automatically via compileKotlin in :common)
RUN ./gradlew :app:installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Copy only the :app distribution from the builder stage (dist name preserved via applicationName)
COPY --from=builder /app/app/build/install/spovishun/ ./

# Run as non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

ENTRYPOINT ["./bin/spovishun"]
