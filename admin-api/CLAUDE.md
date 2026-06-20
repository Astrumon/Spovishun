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

Docker data comes only from docker-socket-proxy (GET-only: `INFO=1`, `CONTAINERS=1`) at
`DOCKER_API_URL`. Missing/invalid bearer token → 401.

## DTO contract
`dto/` is the deliberate single source of the JSON contract that the future `spovishun-admin` client
duplicates — keep it engine-agnostic and stable.

## Config (env)
`ADMIN_API_ENABLED`, `ADMIN_API_BIND` (tailnet IP in prod, `127.0.0.1` dev), `ADMIN_API_PORT`,
`ADMIN_API_TOKEN` (required when enabled), `DOCKER_API_URL`.

## Testing
Module-local `src/test` (JUnit5 + MockK), like `:data`. Auth branches via Ktor `testApplication`
with mocked `DockerApiClient`/`ServerHealthRepository`; `DockerResponseMapper` covered by pure tests
(JSON → DTO, log de-framing). Do NOT unit test `AdminApiServer` or the Koin module.
