---
name: docker-deployment
description: Use this skill when containerizing Kotlin applications, writing Dockerfiles, configuring docker-compose, deploying to cloud VMs, or working with GitHub Container Registry (ghcr.io). Triggers on questions about Docker, deployment, environment variables, Oracle Cloud, or image publishing.
---

# Docker & Deployment (Kotlin Apps)

You are an expert in containerizing Kotlin applications and setting up reliable deployments.

## Dockerfile (Multi-stage, installDist)

This project uses `installDist` (not `shadowJar`) — produces a distribution directory with a launch script.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Cache dependency layer
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
COPY --from=builder /app/build/install/spovishun/ ./

RUN addgroup -S app && adduser -S app -G app
USER app

ENTRYPOINT ["./bin/spovishun"]
```

## docker-compose.yml (Prod — no local DB)

For production, the bot connects to a cloud DB (Neon). No local postgres needed.

```yaml
services:
  bot:
    image: ghcr.io/astrumon/spovishun:latest
    container_name: spovishun-bot
    env_file: .env
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    container_name: spovishun-db
    profiles: ["dev"]   # only starts with: docker compose --profile dev up
    environment:
      POSTGRES_DB: spovishun_dev
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DEV_DATABASE_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d spovishun_dev"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  pgdata:
```

## .env (prod)

```env
TELEGRAM_BOT_TOKEN=<token>
ADMINS=<id1,id2>
PROFILE=prod
PROD_DATABASE_URL=jdbc:postgresql://ep-xxx.region.aws.neon.tech/neondb?sslmode=require
PROD_DATABASE_DRIVER=org.postgresql.Driver
PROD_DATABASE_USERNAME=neondb_owner
PROD_DATABASE_PASSWORD=<password>
PROD_DATABASE_POOL_SIZE=5
```

## Deployment Workflow (low-RAM server)

The production server (Oracle Cloud E2.1.Micro, 1 GB RAM) cannot run `gradle build` inside Docker.
Strategy: **build locally → push to ghcr.io → server pulls and runs**.

### Build & Push (local machine)

```bash
docker build -t ghcr.io/astrumon/spovishun:latest .
docker push ghcr.io/astrumon/spovishun:latest
```

Requires prior login:
```bash
echo <PAT> | docker login ghcr.io -u astrumon --password-stdin
```

PAT scopes required: `write:packages`, `read:packages`.
Image name MUST be lowercase — ghcr.io enforces this.

### Deploy (on server)

```bash
ssh -i ~/.ssh/oracle_key ubuntu@141.147.4.30
cd ~/Spovishun
docker compose pull bot
docker compose up -d
```

### Verify

```bash
docker compose ps
docker compose logs --tail=50 bot
```

Expect in logs:
- `Bot started successfully`
- `Schema "public" is up to date` or `Applied X migration(s)`

## Updating the Bot

Every new release:

1. **Local:** build and push new image
   ```bash
   docker build -t ghcr.io/astrumon/spovishun:latest .
   docker push ghcr.io/astrumon/spovishun:latest
   ```

2. **Server:** pull and restart
   ```bash
   cd ~/Spovishun
   docker compose pull bot
   docker compose up -d
   ```

Downtime: ~2-5 seconds.

## Flyway Migrations

Migrations run automatically on container start — no manual steps needed.
Add new migration files under `src/main/resources/db/migration/postgresql/`, deploy new image, done.

## Security Rules
- Never hardcode credentials — use `.env` (not committed to git)
- Run containers as non-root user
- Use `restart: unless-stopped` for 24/7 availability
- Use `profiles: ["dev"]` for services not needed in prod
- Pin base image versions — avoid unversioned `latest` for base images
