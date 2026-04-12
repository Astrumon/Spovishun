---
name: dependency-injection-architecture
description: Use this skill when designing or implementing dependency injection with Koin or Spring in Kotlin projects. Triggers on questions about DI containers, module structure, layered architecture, or adding new services/repositories.
---

# Dependency Injection & Architecture (Kotlin)

You are an expert in clean architecture and dependency injection for Kotlin applications.

## Layer Architecture

```
presentation (commands, handlers)  ← handles input (Telegram commands)
    ↓
domain (services)                  ← business logic, orchestration
    ↓
data (repositories)                ← data access (DB, in-memory)
    ↓
common                             ← pure Kotlin utilities, zero project imports
```

**Rules:**
- `presentation` → `domain` ← `data` (allowed dependency direction)
- `common` is accessible from all layers
- `di` wires everything and knows all layers
- Each layer only knows about the layer directly below it
- Repositories return domain objects, not DB entities

## Hard Rules per Layer (Spovishun)
- `domain/` — no Telegram SDK, no Exposed/JDBC, no Koin, no `Dispatchers.IO`
- `data/` — no Telegram SDK, never call services
- `common/` — pure Kotlin only, zero project imports
- `presentation/` — no Exposed/DB imports; no business logic in Command classes
- Only `data/db/DatabaseFactory.kt` may use `Dispatchers.IO`

## Koin Setup Pattern
```kotlin
val appModule = module {
    // Data layer
    single<UserRepository> { UserRepositoryImpl(get()) }

    // Service layer
    single { UserService(get()) }
    single { NotificationService(get(), get()) }

    // Controller layer
    single { BotController(get(), get()) }
}

fun main() {
    startKoin {
        modules(appModule)
    }
}
```

## Profile-Based Configuration
```kotlin
val devModule = module {
    single<MemberRepository> { MemberRepositoryMockImpl() }
}
val prodModule = module {
    single<MemberRepository> { MemberRepositoryImpl() }
}

val profile = System.getenv("PROFILE") ?: "dev"
startKoin { modules(if (profile == "prod") prodModule else devModule) }
```

## DI Best Practices
- Prefer constructor injection — dependencies are explicit and testable
- Use interfaces for all services and repositories — enables mocking
- `single` for stateful/expensive objects (DB connections, services)
- `factory` for lightweight, stateless objects created per request
- Never inject the DI container itself — it's a service locator anti-pattern
- All repository bindings use the interface type: `single<MemberRepository> { MemberRepositoryImpl() }`

## Naming Conventions
- Interface: `UserRepository`
- Implementation: `UserRepositoryImpl` (DB), `UserRepositoryMockImpl` (in-memory)
- Module file: `DevRepositoryModule.kt`, `ProdRepositoryModule.kt`, `ServiceModule.kt`
- Never use `UseCase` — use `Service` instead
