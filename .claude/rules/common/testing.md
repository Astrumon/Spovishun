# Testing Rules

## Approach
- TDD by default: write the test first, then implement, then refactor
- NEVER write production code to pass a test you haven't run and seen fail first
- Tests are first-class code — same quality standards as production code

## Stack (Kotlin)
- Unit tests: JUnit5 + MockK
- Coroutine tests: `runTest {}` from `kotlinx-coroutines-test`
- Mocking: `mockk<Dependency>()`, `coEvery`, `coVerify`
- Reset state: `clearAllMocks()` in `@BeforeTest`

## What to Test
- Domain layer (services): ALWAYS unit tested — mock repositories
- Presentation layer (controllers): ALWAYS unit tested — mock services
- Data layer (repositories): integration tested against MockImpl, not unit mocked
- NEVER unit test: Koin modules, `TelegramBot`, `MessageHandler`, `DatabaseFactory`

## Coverage
- Minimum 80% line coverage for domain and presentation layers
- Every public function in a service or controller needs at least one test
- Cover both success and failure paths (`ResultContainer.Success` and `Failure`)

## Naming
- Pattern: `fun should_doX_when_conditionY()`
- Be descriptive — the name should explain the scenario without reading the body

## Forbidden
- `Thread.sleep()` in tests — use `advanceTimeBy()` or `TestCoroutineScheduler`
- Hardcoded delays or flaky waits
- Tests that never assert anything (no `verify` or `assertEquals`)
- Mocking the class under test
