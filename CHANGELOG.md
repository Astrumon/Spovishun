# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), versioning: [SemVer](https://semver.org/).

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
