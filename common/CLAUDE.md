# :common

Pure-Kotlin shared kernel. Has no dependency on any other project module and pulls in no framework
(no Koin, Exposed, or Telegram SDK). Every other module depends on `:common`.

Packages (under `common/`): `result/` (`ResultContainer`), `exception/` (`BaseException` +
domain/not-found exceptions), `extension/` (`ResultExtensions` — `orFailure`/`collectAll`),
`util/` (HTML escaping, `UsernameInputSanitizer`, `VersionInfo`).

## Rules
- Keep it framework-free: no `org.koin.*`, `org.jetbrains.exposed.*`, `com.github.kotlintelegrambot.*`,
  and no dependency on `:domain` / `:data` / `:bot` / `:app`.
- `ResultContainer<T>` is the project's Result type (`Success<T>` / `Failure(BaseException)`) — not
  Kotlin's `Result`. Services and repository interfaces return it; chain with `.flatMap {}`, resolve
  with `.fold(...)`, wrap throwing calls with `ResultContainer.catching { }`.
- `VersionInfo` is generated from the root `version` via `:common`'s `generateVersionInfo` task.
