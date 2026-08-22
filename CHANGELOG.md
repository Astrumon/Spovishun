# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), versioning: [SemVer](https://semver.org/).

---

## [1.8.2] - 2026-08-22

### Changed
- spovishun-skills оновлено 1.21.0 → 1.28.0: Notion-читачі тепер піднімають вкладені блоки, тож
  промпт задачі всередині toggle більше не приїжджає порожнім (spovishun-191).

### Fixed
- Шедулери більше не ковтають скасування: `stopKoin()` тепер справді зупиняє пас, а не дає йому
  дренажити решту списку, і скасоване привітання з днем народження більше не записується як
  невдале з повторним надсиланням наступного запуску. Увімкнено detekt-правила
  `SuspendFunSwallowedCancellation` і `GlobalCoroutineUsage` (spovishun-190).

---

## [1.8.1] - 2026-08-09

### Added
- `/newgroup` accepts `$icon=` and `$mark=` alongside the group name, so a group can be created
  fully configured without a follow-up `/editg` (spovishun-182).
- The readiness poll counts its initiator as ready — the member who runs `/ping` or `/all` in
  readiness mode no longer has to press the button (spovishun-183).

### Changed
- README and CLAUDE.md synced with the codebase (spovishun-178).
- The e2e testing doc rewritten (spovishun-181).

---

## [1.8.0] - 2026-08-09

### Added
- Inline pickers for the admin group and role commands — the group is chosen from buttons instead
  of typed by name (spovishun-123), and `/random` gained the same picker (spovishun-122).
- Readiness-check mode for `/ping` and `/all` — members confirm readiness from the message itself
  (spovishun-119), and the `/ping` inline menu now offers a default "all" option (spovishun-151).
- `/editg` — group settings editing: `$icon` (spovishun-32), extended to the multi-parameter
  `$name=` / `$icon=` / `$mark=` form for rename and `/ping` mark (spovishun-180, migration
  `V15__add_group_ping_mark.sql`).
- `/language` — per-chat language selection; bot messages and release notes render in the chosen
  language (spovishun-152).
- Optional `$b DD.MM` flag in `/register` — the birthday can be set at registration time
  (spovishun-81).
- Prefix-based `CallbackRouter` for inline callbacks (spovishun-121).
- Chat context on every log line (spovishun-168).
- SSE live log streaming endpoint in `:admin-api` (spovishun-111).
- `/release` and `/hotfix` GitFlow automation skills (spovishun-80).

### Changed
- Restructured the `:bot` presentation layer (spovishun-172); the Koin graph is bound with the
  constructor DSL (spovishun-176), the admin module renamed and the graph verified (spovishun-156),
  coroutine bindings moved and the graph closed on shutdown (spovishun-155).
- Canonicalized group resolution and collapsed settings writes into one patch (spovishun-174);
  batched group member reads and resolved groups by id (spovishun-171).
- Removed `safeDbTransaction` — `safeDbQuery` is the single DB entry point (spovishun-173).
- Encapsulated Docker client transport errors behind the repository (spovishun-143).
- Revived the `ResultContainer` combinators and dropped dead code (spovishun-170); `AutoRegisterService`
  now requires explicit caches (spovishun-154).
- Closed integration coverage gaps (spovishun-161); the e2e suite asserts real Telegram responses
  (spovishun-160).

### Fixed
- Birthday greetings render a proper Telegram mention instead of plain text (spovishun-141).

### Build
- detekt runs with type resolution and thresholds aligned to the project style rules, with
  per-source-set baselines (spovishun-169).
- CI caches and parallelizes Gradle builds (spovishun-159).
- `:admin-api` added to the Dockerfile multi-module build.
- spovishun-skills updated to v1.21.0.

---

## [1.7.0] - 2026-06-20

### Added
- Admin observability API (`:admin-api`): an embedded read-only Ktor server running in the bot
  process, bearer-authenticated and bound to the tailnet interface only, exposing `/api/v1`
  endpoints for health (DB connectivity + size), metrics, containers and container logs over the
  read-only `docker-socket-proxy` (spovishun-110).

### Changed
- Reorganized `:domain` and `:data` by bounded context (`bot/` + `admin/`); shared DB infra
  (`DatabaseFactory`, `DataSourceFactory`, `DatabaseConfig`, `ExposedExtensions`) and the migration
  dev-tool remain context-free. Internal only — no change to bot behavior or commands.

### Fixed
- Empty release-notes entries no longer trigger a startup auto-broadcast or render a bare `/whatsnew`
  reply, so internal-only releases do not spam chats (spovishun-134).

### Build
- `docker-compose.yml` wires the admin API environment and publishes its port on the tailnet
  interface only (`ADMIN_API_BIND_IP`, default `127.0.0.1` so it is never exposed on the public IP).

---

## [1.6.0] - 2026-06-20

### Changed
- Migrated the codebase to a multi-module Gradle build (`:common :domain :data :bot :app`);
  `:app` is the composition root that wires everything via Koin. No change to bot behavior or commands.

### Added
- Read-only `docker-socket-proxy` (GET-only Docker Engine API, socket mounted read-only, no public port)
  on the `prod` profile, plus Tailscale-based private admin access — groundwork for a future admin API.
- ktlint (formatting, hard CI gate) and detekt (static analysis, non-blocking) with per-module baselines.
- Scope-level `CoroutineExceptionHandler` on scheduler coroutine scopes.

### Fixed
- Deploy workflow now syncs the VM working tree to `origin/main` before bringing the stack up,
  so `docker-compose.yml` changes ship to production instead of drifting.

### Tooling
- `.claude/` automation stack migrated to the `spovishun-skills` plugin.

---

## [1.5.0] - 2026-05-12

### Added
- `/random [group]` command — picks a random member from the chat or from a named group
- `/whatsnew $on` / `$off` — admin-only per-chat toggle for release note broadcasts
- `/whatsnew $h` — displays full version history
- `chats.announcements_enabled` column (V11 migration) for per-chat broadcast preference
- `CommandResponse.Silent` — suppresses bot message when release notes are empty

---

## [1.4.0] - 2026-05-11

### Added
- `/whatsnew` command — shows the latest release notes; `/whatsnew $h` displays full version history
- Auto-broadcast release notes to all chats when bot starts with a new version
- `bot_meta` generic KV table (V10 migration) for persisting last-notified version
- `release_notes.json` as the single source of truth for version history

---

## [1.3.1] - 2026-05-09

### Fixed
- Birthday greeting now includes the Telegram username alongside the first name (`firstName @username`), making the mention identifiable in group chats

---

## [1.3.0] - 2026-05-08

### Added
- Birthday greeting feature — bot automatically congratulates group members on their birthday

### Changed
- Sanitize and validate username input across all group commands

---

## [1.2.0] - 2026-05-05

### Added
- Ping group selection menu with inline keyboard and callback handler
- Anti-piracy protection and security hardening
- 6 Claude Code sub-agents (kotlin-reviewer, database-reviewer, doc-updater, and others)

### Changed
- Migrate prod DB to self-hosted PostgreSQL on Oracle VM
- Typesafe bot messages — replace raw strings with sealed class hierarchy
- Standardize `GroupRepositoryImpl` to use `toGroup()` mapper

---

## [1.1.0] - 2026-04-28

### Added
- Diagram-design skill for editorial technical diagrams
- GitHub Actions deploy workflow (CI/CD)
- Notion CLI scripts with integration and fallback tests
- Notion lib foundation and advanced optimizations (format flags, section reads)
- Auto-pick single task and recover orphaned in-progress tasks
- Admin pre-registration feature
- Interactive task picker and auto-close CI workflow
- e2e DB cleanup infrastructure and multi-chat user tests

### Changed
- Lazy-load 4 monolithic skills via references/
- Deduplicate Notion skills (−30% size)
- Wire hook and skills through scripts/notion/lib
- Replace MockImpl repos with real DB for integration tests

### Fixed
- Remove user-identifiable data from presentation-layer logs

---

## [1.0.0] - 2026-04-12

### Added
- Members-chats M2M relationship
- /grantrole command in /start welcome message
- Command registry
- Dockerized bot with dev/prod profiles
- Profile-based DB config (local PostgreSQL / Neon)
- Idea planning pipeline skills and Notion navigator
- update-doc-full skill and doc-updater infrastructure
- Session-end hook and newtask skill
- e2e test suite with real Telegram API

### Changed
- Unified dev/prod data layer
