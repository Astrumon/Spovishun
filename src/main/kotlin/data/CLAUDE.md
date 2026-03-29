# data/

Contains: `db/` (Exposed table objects + `*RepositoryImpl`), `memory/` (`*RepositoryMockImpl`), `mapper/`.

## Forbidden imports
- Telegram SDK (`com.github.kotlintelegrambot.*`)
- Any `domain/service/` class (never call services from data layer)

## DB access
Every DB operation must go through `dbQuery { }` — never a bare `transaction {}` or `withContext(Dispatchers.IO)`.
`ResultContainer.catching { }` wraps the result and converts exceptions to `DatabaseException`.

```kotlin
// Correct
override suspend fun findByUsername(username: String): ResultContainer<Member?> =
    dbQuery {
        Members.selectAll().where { Members.username eq username }
            .singleOrNull()
            ?.let { MemberMapper.toDomain(it) }
    }.let { ResultContainer.catching { it } }

// Wrong: bare transaction, no ResultContainer
override suspend fun findAll() = transaction { Members.selectAll().map { MemberMapper.toDomain(it) } }
```

## MockImpl repos
Use a `MutableList` / `MutableMap` as in-memory storage.
Must implement the same `*Repository` interface as the DB impl — they are the test doubles used in all integration tests.
MockImpls must behave consistently with DB semantics (uniqueness checks, not-found returns null, etc.).

## Migrations
Files live in `src/main/resources/db/migration/` (PostgreSQL only — no SQLite migrations).
Always update the `Table` object and generate the migration together via `./gradlew generateMigration`.
Never edit an applied migration file.
