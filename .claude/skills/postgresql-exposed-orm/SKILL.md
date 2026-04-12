---
name: postgresql-exposed-orm
description: Use this skill when working with Kotlin Exposed ORM and PostgreSQL. Triggers on questions about database schema design, Exposed tables, transactions, migrations (Flyway/Liquibase), or SQL queries in Kotlin projects.
---

# PostgreSQL with Kotlin Exposed ORM

You are an expert in database design and Kotlin Exposed ORM. You help design efficient schemas, write type-safe queries, and implement migrations.

## Table Definition Best Practices
```kotlin
object Users : LongIdTable("users") {
    val username  = varchar("username", 50).uniqueIndex()
    val telegramId = long("telegram_id").uniqueIndex()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val isActive  = bool("is_active").default(true)
}

object Groups : LongIdTable("groups") {
    val name   = varchar("name", 100)
    val chatId = long("chat_id").index()
}
```

## DSL vs DAO
- **DSL**: Use for complex queries, reporting, bulk operations
- **DAO**: Use for CRUD on individual entities with ORM-style access
- Prefer DSL for read-heavy operations — more explicit and efficient

## Transaction Rules
- All DB operations must be wrapped in `transaction { }` or `newSuspendedTransaction { }`
- Use `newSuspendedTransaction(Dispatchers.IO)` for coroutine contexts
- Keep transactions short — avoid long-running business logic inside
- Use `repetitionAttempts` for retry on deadlock

## Query Patterns
```kotlin
// Find with condition
fun findByTelegramId(telegramId: Long): User? = transaction {
    User.find { Users.telegramId eq telegramId }.firstOrNull()
}

// Batch insert
fun insertMembers(members: List<String>) = transaction {
    Members.batchInsert(members) { username ->
        this[Members.username] = username
    }
}

// Update with where
fun deactivateUser(id: Long) = transaction {
    Users.update({ Users.id eq id }) {
        it[isActive] = false
    }
}
```

## Migrations with Flyway
- Store SQL migrations in `resources/db/migration/`
- Naming: `V{version}__{description}.sql` (e.g., `V1__create_users.sql`)
- Never modify existing migration files
- Run `Flyway.configure().dataSource(...).load().migrate()` on startup

## Connection Pool (HikariCP)
```kotlin
val config = HikariConfig().apply {
    jdbcUrl         = "jdbc:postgresql://localhost:5432/mydb"
    driverClassName = "org.postgresql.Driver"
    maximumPoolSize = 10
    minimumIdle     = 2
    connectionTimeout = 30_000
}
Database.connect(HikariDataSource(config))
```

## Spovishun-Specific Rules
- Always use `safeDbQuery { }` — never bare `transaction { }` or manual `ResultContainer.catching { dbQuery { } }`
- `safeDbQuery` and `safeDbTransaction` live in `data/db/DatabaseFactory.kt`
- Only `DatabaseFactory.kt` may use `Dispatchers.IO`

## Output Format
When reviewing or designing DB code:
1. **Schema assessment** — table structure, index coverage, constraint correctness
2. **Query analysis** — N+1 risks, missing indexes, transaction boundaries
3. **Migration note** — if DDL changes are needed, generate via `./gradlew generateMigration`
4. **Code issues** — violations of `safeDbQuery` rule or layer boundary (data → domain)
