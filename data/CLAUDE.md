# :data

Persistence module. Depends on `:domain` (implements its repository interfaces) and `:common`.

Packages: `db/table/` (Exposed `Table` objects), `db/repository/` (`*RepositoryImpl`),
`db/` (`DatabaseFactory`, `DatabaseConfig`), `mapper/`, `releasenotes/`, `tools/` (MigrationGenerator).

## Forbidden dependencies
- Telegram SDK (`com.github.kotlintelegrambot.*`)
- Any `:domain` service class (never call services from the data layer)
- A Gradle dependency on `:bot` or `:app`

## DB access
Every DB operation must use `safeDbQuery { }` from `data.db.DatabaseFactory` — never a bare `transaction {}`, `withContext(Dispatchers.IO)`, or manual `ResultContainer.catching { dbQuery { } }`.
`safeDbQuery` handles both dispatching and exception-to-`DatabaseException` conversion in one call.
`DatabaseFactory` is the only place allowed to touch `Dispatchers.IO`.

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

## Testing the data layer
There are no in-memory MockImpl repositories — repositories are exercised against a real database:
- **Unit** (`src/test/`) — H2 in PostgreSQL-compatibility mode via `H2TestDatabaseFactory`.
- **Integration** (`:app` `integrationTest`) — a real PostgreSQL; the suite skips itself when
  `E2E_DATABASE_URL` is unset.

## Migrations
Files in `src/main/resources/db/migration/postgresql/` — Flyway runs against PostgreSQL for both dev and prod.
Always update the `Table` object and generate the migration together via `./gradlew generateMigration`
(registered in this module). Never edit an applied migration file.
