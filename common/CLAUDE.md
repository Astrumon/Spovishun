# :common

Pure-Kotlin shared kernel. Has no dependency on any other project module and pulls in no framework
(no Koin, Exposed, or Telegram SDK). Every other module depends on `:common`.

Packages (under `common/`): `result/` (`ResultContainer`), `exception/` (`BaseException` +
domain/not-found exceptions), `extension/` (`ResultExtensions` — `orFailure`),
`util/` (HTML escaping, `UsernameInputSanitizer`, `VersionInfo`).

## Rules
- Keep it framework-free: no `org.koin.*`, `org.jetbrains.exposed.*`, `com.github.kotlintelegrambot.*`,
  and no dependency on `:domain` / `:data` / `:bot` / `:app`.
- `ResultContainer<T>` is the project's Result type (`Success<T>` / `Failure(BaseException)`) — not
  Kotlin's `Result`. Services and repository interfaces return it; chain with `.flatMap {}`, resolve
  with `.fold(...)`, wrap throwing calls with `ResultContainer.catching { }`.
- `catching` re-throws `CancellationException` instead of capturing it (spovishun-173). It is an
  `Exception`, so the generic fallback used to swallow it and hand the caller a fabricated `Failure`
  while the coroutine was supposed to be unwinding. Every DB call reaches this through `safeDbQuery`.
- `VersionInfo` is generated from the root `version` via `:common`'s `generateVersionInfo` task.

## Testing
Own `src/test`, run by `./gradlew test` like every other module's. The tests are as framework-free as
the code: plain JUnit5 assertions over pure functions, no MockK and no coroutine scheduler.
