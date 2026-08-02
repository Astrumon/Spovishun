---
name: hotfix
description: "Automates the GitFlow hotfix flow for Spovishun: hotfix branch from main, patch-only version bump, CHANGELOG.md + release_notes.json generation, PR to main, git tag after merge, sync-back PR to develop, branch cleanup. User-invocable via /hotfix <version>. Triggers: hotfix, hot fix, production fix release, хотфікс, гарячий фікс, терміновий фікс на проді."
user_invocable: true
---
# Hotfix

Variant of the `release` skill for urgent production fixes: branches from **`main`**
(not `develop`) and allows a **patch bump only**. The tail of the flow (notes → confirm →
commit → PR → tag → sync back → cleanup) is identical to `/release` — the shared rationale
lives in `.claude/skills/release/SKILL.md`.

**Project-owned skill** — NOT part of the spovishun-skills plugin manifest; the plugin must
never regenerate or prune it.

## Hard rules

Same as the `release` skill: never squash release merges, tag only after the `main` merge,
always sync back to `develop`, confirm before tag push and branch deletion, never
`--no-verify` or force-push, stop on any failure. Additionally:

- **Fix commits only** on a hotfix branch — no features, no refactors.
- **Never edit/stage/commit** `common/src/main/kotlin/common/util/VersionInfo.kt` — it is
  gitignored and auto-generated from the root `build.gradle.kts` version.

## Workflow

### Step 1: Validate the version argument

**1a.** Required: `/hotfix <version>` (e.g. `/hotfix 1.7.1`). Strict SemVer `X.Y.Z`.

**1b.** Read the current version from the root `build.gradle.kts` (`version = "X.Y.Z"`).
Enforce **patch-only bump**: new `X.Y` must equal current `X.Y`, new `Z` must be
current `Z + 1`. Anything else (minor/major jump, gap in patch) → abort: that is a
regular release, use `/release`.

### Step 2: Preconditions and branch

- Clean working tree (`git status --porcelain` empty) and `gh auth status` OK.
- If `hotfix/spovishun-{version}` **already exists** (the user prepared the fix before
  invoking the skill): `git checkout hotfix/spovishun-{version}` and verify it branches
  from current `main`; skip creation.
- Otherwise: `git checkout main && git pull origin main`, then
  `git checkout -b hotfix/spovishun-{version}` — from **main**.

### Step 3: The fix

If the branch was just created, the user implements the fix now (with tests) — this skill
does not write the fix itself; it resumes once the fix is committed on the hotfix branch.
If the branch pre-existed with the fix commits, continue directly.

### Step 4: Version bump

Edit root `build.gradle.kts` → `version = "{version}"`. Consistency check:
`./gradlew :common:generateVersionInfo` regenerates `VersionInfo.kt` with the new version.

### Step 5: Release notes

Same generation as `release` Step 5 — last tag via
`git tag --list "v*" --sort=-v:refname | head -1` (not `git describe`; see release Step 5a) —
with the range `{last-tag}..HEAD` on the hotfix branch (typically just the fix commits): group `fix` → Fixed (the dominant case),
`feat` → Added should not appear (see Hard rules), rest → Changed. Produce the
`## [{version}] - {date}` CHANGELOG section and the prepended
`{version, date, changes{uk[], en[]}}` record for `data/src/main/resources/release_notes.json`.
Both language keys are required (spovishun-152); an empty `changes` object (`{}`) suppresses the
`/whatsnew` broadcast (spovishun-134) — for a user-visible production fix it should normally be non-empty.

### Step 6: Confirm → commit → push

Show both fragments, wait for approval. Stage exactly root `build.gradle.kts`,
`CHANGELOG.md`, `data/src/main/resources/release_notes.json`; commit
`chore: bump version to {version}`; push the branch.

### Step 7: PR to main → tag → sync back → cleanup

Identical to `release` Steps 8–11, substituting the branch name:

1. PR `hotfix/spovishun-{version}` → `main`, title `release: v{version}` — merge commit,
   no squash; wait for verified merge (`gh pr view --json state,mergedAt`).
2. `git checkout main && git pull`, `git tag v{version}`, confirm, `git push origin v{version}`.
3. PR `hotfix/spovishun-{version}` → `develop`, title
   `chore: sync release v{version} back to develop`; wait for verified merge.
4. After confirmation: delete the hotfix branch remotely and locally.

## Scope guard

Patch releases from `main` only. No deploys, no CI edits, no Notion writes. For a regular
minor/major release from `develop`, use the `release` skill.

## Related Skills

- `release` — the full release flow from `develop`; this skill reuses its Steps 8–11 tail
  and hard rules (`.claude/skills/release/SKILL.md`).

## Example

`/hotfix 1.7.1` with current version `1.7.0`: branch `hotfix/spovishun-1.7.1` from fresh
`main`; after the fix commits land, version bumps to `1.7.1`, CHANGELOG/`release_notes.json`
records are generated from `v1.7.0..HEAD` and approved; one bump commit is pushed;
PR `release: v1.7.1` → `main`; after merge tag `v1.7.1` is pushed; sync-back PR → `develop`;
branch deleted after the second merge. `/hotfix 1.8.0` aborts: not a patch bump — use
`/release`.
