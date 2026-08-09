# :domain

Pure business logic module. Depends only on `:common` (no Gradle dependency on `:data` or `:bot`).

Organized by bounded context, each keeping its own layer subpackages:
- `bot/` — the Telegram bot domain: `bot/model/`, `bot/repository/` (interfaces only), `bot/service/`,
  `bot/cache/`, `bot/config/` (package `com.ua.astrumon.domain.bot.*`).
- `admin/` — the admin observability API domain (spovishun-110): `admin/model/` (`ServerHealth`),
  `admin/repository/` (`ServerHealthRepository`) (package `com.ua.astrumon.domain.admin.*`).

New domain types belong under the context they serve; add layer subpackages (`model/`, `repository/`,
`service/`) within a context as needed.

## Forbidden dependencies
- Telegram SDK (`com.github.kotlintelegrambot.*`)
- Exposed / JDBC (`org.jetbrains.exposed.*`, `java.sql.*`)
- Koin (`org.koin.*`)
- `Dispatchers.IO` (or any coroutine dispatcher assignment)
- A Gradle dependency on `:data` or `:bot` (the build must keep `:domain` free of them)

## Repository interfaces
Return `ResultContainer<T>`. Nullable result is allowed at the interface level (e.g. `ResultContainer<Member?>`).
Convert null → `ResourceNotFoundException` inside the **service**, not the repository.

## Services
Compose repository calls via `.flatMap {}`. Always return `ResultContainer<T>`.

```kotlin
// Correct
suspend fun getMemberByUsername(username: String): ResultContainer<Member> =
    memberRepository.findByUsername(username).flatMap { member ->
        if (member != null) ResultContainer.success(member)
        else ResultContainer.failure(ResourceNotFoundException("Member", username))
    }

// Wrong: raw dispatcher, bare exception
suspend fun getAll(): List<Member> = withContext(Dispatchers.IO) {
    transaction { Members.selectAll().map { it.toMember() } }
}
```

## Access checks
`hasAdminAccess()` / `hasModeratorAccess()` live in `MemberService` and query the DB.
They do NOT call the Telegram API.

## Testing
Own `src/test`, run by `./gradlew test` like every other module's — services are unit-tested here,
not in `:app`. Each service against `mockk<*Repository>()` inside `runTest {}`, with `coEvery` /
`coVerify` and `clearAllMocks()` in `@BeforeTest`. Cover both `ResultContainer` branches: a service
that only proves its success path is untested where it matters.
