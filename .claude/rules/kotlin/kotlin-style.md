# Kotlin Style Rules

## Idiomatic Kotlin
- Prefer `val` over `var` — mutability must be justified
- Use `data class` for value objects and DTOs
- Use `sealed class` / `sealed interface` for closed hierarchies
- `when` expressions on sealed types MUST be exhaustive — no `else` branch
- Use extension functions to add behavior without inheritance

## Nullability
- NEVER use `!!` (non-null assertion) — use `requireNotNull()` or `checkNotNull()` with a message
- Prefer `?.let {}`, `?: return`, or `?: throw` over `!!`
- Nullable return types in domain layer are forbidden — use `ResultContainer` instead

## Functions
- One function = one responsibility
- Max ~20 lines per function — if longer, extract
- NEVER use boolean parameters — use named subclasses, enums, or overloads instead
- Prefer expression body (`= ...`) for single-expression functions

## Error Handling
- NEVER swallow exceptions with empty `catch` blocks
- Business errors: use `sealed class` or `ResultContainer.Failure` — not exceptions
- `CoroutineExceptionHandler` must be set at scope level, not inside `launch {}`
- NEVER use `runCatching` as a substitute for proper error modeling

## Coroutines & DI
- Inject `CoroutineDispatcher` and `CoroutineScope` via Koin — never hardcode `Dispatchers.IO` inside a class
- Tests replace dispatchers with `StandardTestDispatcher()` for deterministic control
- Use `viewModelScope` / injected scope — never create raw `GlobalScope.launch`

## Immutability & Shared State
- `StateFlow` or `Channel` for observable mutable state
- `Mutex` or `AtomicReference` for shared state across coroutines
- NEVER use `@Volatile` as a replacement for proper concurrency primitives

## Exposed ORM
- ALL database access MUST be inside `transaction {}` or `newSuspendedTransaction {}`
- Use `safeDbQuery {}` — never call `dbQuery {}` directly or wrap manually
- Only `DatabaseFactory.kt` may use `Dispatchers.IO`

## Koin
- `single {}` for services, repositories, long-lived objects
- `factory {}` for use cases and short-lived objects
- Constructor injection only — no `by inject()` inside business logic classes
- Bind by interface: `single<MemberRepository> { MemberRepositoryImpl() }`

## Imports
- Always write explicit imports — never rely on star imports (`import com.example.*`)
- When a type from an external library conflicts with a project class name, use `import as`:
  ```kotlin
  import com.github.kotlintelegrambot.Bot as TelegramBot
  ```
- Apply `import as` consistently: if a rename is chosen in one file, use the same alias across all files in the module
- Never leave an ambiguous import that requires a fully-qualified name at the call site — resolve it with `import as` instead

## Naming
- Repository: `XxxRepository` (interface) / `XxxRepositoryImpl` (implementation)
- Use case: `XxxUseCase`
- Handler: `XxxHandler`
- DTO: `XxxDto`
- NEVER abbreviate class names — clarity over brevity
