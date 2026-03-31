# ────────────────────────────────────────────
# Stage 1: Build
# ────────────────────────────────────────────
FROM gradle:9.0-jdk21 AS build

WORKDIR /app

# Copy dependency descriptors first for layer caching
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/

# Download dependencies (cached layer if above files unchanged)
RUN gradle dependencies --no-daemon --quiet || true

# Copy source and build distribution
COPY src/ src/
RUN gradle installDist --no-daemon

# ────────────────────────────────────────────
# Stage 2: Runtime
# ────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/build/install/spovishun/ ./

# Non-root user for security
RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

ENTRYPOINT ["bin/spovishun"]
