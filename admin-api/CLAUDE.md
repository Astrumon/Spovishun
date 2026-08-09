# :admin-api

Embedded read-only HTTP observability API (spovishun-110). Runs inside the bot process as a Ktor
(CIO) server, started from `:app` alongside Telegram long-polling, bound to the tailnet interface
only and guarded by a bearer token. Depends on `:domain`, `:data`, `:common`.

Packages (under `admin/`): `config/` (`AdminApiConfig.fromEnv`), `dto/` (the `@Serializable` wire
contract), `docker/` (`DockerApiClient` + raw models + pure `DockerResponseMapper`), `auth/`
(`TokenAuthenticator`, constant-time compare), `server/` (`AdminApiServer` lifecycle +
`adminApiModule` Ktor wiring/routes).

## Forbidden dependencies
- Exposed / JDBC (`org.jetbrains.exposed.*`) — DB access goes through the `:domain`
  `ServerHealthRepository` port; the impl lives in `:data`.
- A Gradle dependency on `:bot` or `:app`.
- Telegram SDK.

## Endpoints (all under bearer auth, `/api/v1`)
- `GET /health` — DB connectivity + `pg_database_size` via `ServerHealthRepository`.
- `GET /metrics` — Docker `/info` + per-running-container stats (memory + cpu%).
- `GET /containers` — `/containers/json`.
- `GET /containers/{id}/logs?tail=N` — de-multiplexed container logs (default tail 100).
- `GET /containers/{id}/logs/stream` — SSE live tail (spovishun-111). Relays the upstream
  `logs?follow=true&timestamps=true` stream; each message is `event: log` + `data:` = `LogLineDto`
  JSON `{ts, stream, line}`. `tail=0` upstream → only new lines; the snapshot stays on the GET above.
  Client disconnect cancels the SSE coroutine, which releases the upstream connection.

Docker data comes only from docker-socket-proxy (GET-only: `INFO=1`, `CONTAINERS=1`) at
`DOCKER_API_URL`; the live stream adds only `follow=true&timestamps=true` (still GET). Missing/invalid
bearer token → 401.

## DTO contract
`dto/` is the deliberate single source of the JSON contract that the future `spovishun-admin` client
duplicates — keep it engine-agnostic and stable.

## Config (env)
Read by `AdminApiConfig.fromEnv()` (the module owns its own env contract; composition root stays
oblivious). Full sample lives in the repo `.env.example`.

| Env var | Default | Purpose |
|---|---|---|
| `ADMIN_API_ENABLED` | `false` | Master switch. When `true`, `ADMIN_API_TOKEN` is required (fail-fast `require`). |
| `ADMIN_API_BIND` | `127.0.0.1` | Interface the Ktor server binds **inside** the process/container. In prod docker-compose set to `0.0.0.0` — tailnet-only exposure is enforced by the host port publish, not this bind. |
| `ADMIN_API_PORT` | `8081` | Listen port. |
| `ADMIN_API_TOKEN` | — | Bearer secret (constant-time compared). **Confidential — stored in Bitwarden**, never committed. Generate with `openssl rand -hex 32`. |
| `DOCKER_API_URL` | `http://docker-socket-proxy:2375` | Read-only Docker Engine API via docker-socket-proxy (GET-only: `INFO=1`, `CONTAINERS=1`). |
| `ADMIN_API_BIND_IP` | `127.0.0.1` | **docker-compose only** (not read by the app) — host IP the container port is published on. Set to the VM tailnet IP in prod; NEVER `0.0.0.0`. |

### Base URLs
- **Dev (local):** `http://127.0.0.1:8081/api/v1`
- **Prod (tailnet only):** `http://<VM-tailnet-IP>:8081/api/v1` (e.g. `http://100.105.149.58:8081/api/v1`) — reachable only from the Tailscale network.
- All requests require `Authorization: Bearer <ADMIN_API_TOKEN>`; missing/invalid → 401.

## Testing
Module-local `src/test` (JUnit5 + MockK), like `:data`. Auth branches via Ktor `testApplication`
with mocked `DockerApiClient`/`ServerHealthRepository`; `DockerResponseMapper` covered by pure tests
(JSON → DTO, log de-framing). Do NOT unit test `AdminApiServer` or the Koin module.
