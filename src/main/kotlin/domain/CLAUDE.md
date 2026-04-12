# domain/

Contains: `model/`, `repository/` (interfaces only), `service/`.

## Forbidden imports
- Telegram SDK (`com.github.kotlintelegrambot.*`)
- Exposed / JDBC (`org.jetbrains.exposed.*`, `java.sql.*`)
- Koin (`org.koin.*`)
- `Dispatchers.IO` (or any coroutine dispatcher assignment)
- Any `data/` or `presentation/` package

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

