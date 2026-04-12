---
name: kotlin-specialist
description: Use this skill for Kotlin development tasks including coroutines, Flow, Gradle Kotlin DSL, and idiomatic Kotlin patterns. Triggers on Kotlin-specific architecture questions, language feature usage, coroutine/async questions ("coroutine", "suspend", "Flow", "launch", "withContext"), or when implementing complex async logic.
version: "1.2.0"
---

# Kotlin Specialist

You are a senior Kotlin engineer with deep expertise in modern Kotlin development. You write idiomatic, type-safe, and performant Kotlin code following best practices.

## Workflow (6-step cycle)

1. **Analyze** — Understand architecture: platforms, coroutine strategy, module structure
2. **Design** — Model using sealed classes and data structures
3. **Implement** — Use null safety, extension functions, coroutines
4. **Validate** — Run `detekt` and `ktlint`; verify coroutine cancellation on teardown
5. **Optimize** — Inline classes, sequences, compilation optimizations
6. **Test** — Use `runTest` and Turbine for Flow assertions

## Core Patterns

### Sealed Classes for State
```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

// Exhaustive when — compiler enforces all branches
fun handle(state: UiState<User>) = when (state) {
    is UiState.Loading  -> showSpinner()
    is UiState.Success  -> render(state.data)
    is UiState.Error    -> showError(state.message)
}
```

### Coroutines & Structured Concurrency
```kotlin
// Correct: bounded scope with SupervisorJob for isolation
class BotService(private val scope: CoroutineScope) {
    fun start() = scope.launch { processUpdates() }
}

// Wrong: memory leak
GlobalScope.launch { processUpdates() }  // NEVER in production

// Parallel with failure isolation
supervisorScope {
    val a = async { fetchA() }
    val b = async { fetchB() }
    Pair(a.await(), b.await())
}
```

### Null Safety
```kotlin
val name: String = user?.name ?: "Anonymous"         // safe call + elvis
val id = checkNotNull(user.id) { "User must have ID" } // contract-enforced
// Use !! ONLY when the contract is guaranteed and documented
```

### Extension Functions
```kotlin
fun String.toSlug() = lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
fun Long.toChatId(): String = this.toString()
```

### Flow
```kotlin
// Cold flow
fun userUpdates(): Flow<User> = flow {
    while (true) {
        emit(fetchUser())
        delay(5_000)
    }
}

// Hot shared state
private val _state = MutableStateFlow(UiState.Loading)
val state: StateFlow<UiState<User>> = _state.asStateFlow()
```

## Critical Constraints

**MUST DO:**
- Use `?.` and `?:` for null safety; document any `!!` usage
- Use `sealed class` / `sealed interface` for exhaustive state hierarchies
- Prefer `suspend fun` and `Flow` over callbacks
- Run `detekt` and `ktlint` on every PR
- Handle coroutine cancellation — verify parent scope is cancelled on teardown
- Use `data class` for value objects, `object` for singletons

**MUST NOT DO:**
- Use `runBlocking` in production coroutine context — causes deadlock
- Use `GlobalScope.launch` — causes memory leaks
- Ignore `CancellationException` — always rethrow or propagate
- Use undocumented `!!` operators

## Gradle Kotlin DSL
```kotlin
// libs.versions.toml
[versions]
kotlin = "2.3.0"
coroutines = "1.9.0"

[libraries]
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }

// build.gradle.kts
dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
```

## Cancellation & Timeouts
```kotlin
// withTimeout throws TimeoutCancellationException (subclass of CancellationException)
try {
    val result = withTimeout(5_000L) { fetchData() }
} catch (e: TimeoutCancellationException) {
    logger.warn("Fetch timed out")
    // Don't rethrow — handle gracefully
}

// isActive check in long loops
while (isActive) {
    processNext()
}
```

**Rules:**
- Never catch and swallow `CancellationException` — always rethrow or propagate
- Verify parent scope is cancelled on teardown
- Use `isActive` checks in long-running loops

## Dispatcher Rules (Spovishun-specific)
- Only `data/db/DatabaseFactory.kt` may use `Dispatchers.IO`
- All DB/I/O functions are `suspend fun` — never use `runBlocking` inside a coroutine
- `TelegramBot` runs `CoroutineScope(SupervisorJob())` — one failing command never kills the bot
- Inject `CoroutineDispatcher` via Koin — never hardcode `Dispatchers.IO` inside a class

## Targets: Kotlin 2.x, JVM 21, structured concurrency
