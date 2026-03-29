# data/

Contains: `db/` (Exposed table objects + `*RepositoryImpl`), `memory/` (`*RepositoryMockImpl`), `mapper/`.

## Forbidden imports
- Telegram SDK (`com.github.kotlintelegrambot.*`)
- Any `domain/service/` class (never call services from data layer)

## DB access
Every DB operation must use `safeDbQuery { }` from `data.db.DatabaseFactory` — never a bare `transaction {}`, `withContext(Dispatchers.IO)`, or manual `ResultContainer.catching { dbQuery { } }`.
`safeDbQuery` handles both dispatching and exception-to-`DatabaseException` conversion in one call.

```kotlin
// Correct
override suspend fun findByUsername(username: String): ResultContainer<Member?> =
    safeDbQuery {
        Members.selectAll().where { Members.username eq username }
            .singleOrNull()
            ?.let { MemberMapper.toDomain(it) }
    }

// Wrong: manual wrapping, bare transaction, no ResultContainer
override suspend fun findAll() = transaction { Members.selectAll().map { MemberMapper.toDomain(it) } }
override suspend fun findAll() = dbQuery { ... }.let { ResultContainer.catching { it } }
```

## MockImpl repos
Use a `MutableList` / `MutableMap` as in-memory storage.
Must implement the same `*Repository` interface as the DB impl — they are the test doubles used in all integration tests.
MockImpls must behave consistently with DB semantics (uniqueness checks, not-found returns null, etc.).

## Migrations
Files live in `src/main/resources/db/migration/` (PostgreSQL only — no SQLite migrations).
Always update the `Table` object and generate the migration together via `./gradlew generateMigration`.
Never edit an applied migration file.
