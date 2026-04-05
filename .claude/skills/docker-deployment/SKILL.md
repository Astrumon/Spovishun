---
name: docker-deployment
description: Use this skill when containerizing Kotlin applications, writing Dockerfiles, configuring docker-compose with databases, or setting up CI/CD pipelines. Triggers on questions about Docker, deployment, environment variables, or GitHub Actions.
---

# Docker & Deployment (Kotlin Apps)

You are an expert in containerizing Kotlin applications and setting up reliable deployments.

## Dockerfile (Multi-stage)
```dockerfile
# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## docker-compose.yml (Bot + PostgreSQL)
```yaml
services:
  app:
    build: .
    restart: unless-stopped
    environment:
      - BOT_TOKEN=${BOT_TOKEN}
      - DATABASE_URL=jdbc:postgresql://db:5432/spovishun
      - DATABASE_USERNAME=${DB_USER}
      - DATABASE_PASSWORD=${DB_PASSWORD}
      - PROFILE=prod
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: spovishun
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

## CI/CD Pipelines
For GitHub Actions and deployment automation, use the `ci-cd-pipeline-builder` skill — it covers Gradle caching, matrix builds, and SSH deploy stages.

## Security Rules
- Never hardcode credentials — use environment variables or secrets
- Run containers as non-root
- Pin base image versions — avoid `latest` tag
- Add health checks for all services
- Use `.dockerignore` to exclude `.git`, `build/`, local configs
