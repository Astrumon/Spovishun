---
name: code-decomposition
description: Enforces clean code decomposition following Uncle Bob's Clean Code principles and SOLID. Apply whenever writing functions, classes, or modules in any Kotlin/JVM project.
triggers:
  - "write a function"
  - "create a class"
  - "refactor"
  - "implement feature"
  - "add service"
  - "add repository"
---

# Code Decomposition Skill

Apply these rules every time you write or refactor functions, classes, or modules.
The project stack is Kotlin + Ktor + Exposed ORM + Koin DI — all examples use this context.

---

## 1. SOLID Principles

### S — Single Responsibility
One class/function does exactly one thing. If you can describe it with "and", split it.

```kotlin
// BAD — registers AND sends a welcome message
class MemberService(private val repo: MemberRepository, private val bot: TelegramBot) {
    suspend fun registerAndNotify(member: Member) {
        repo.save(member)
        bot.sendMessage(member.chatId, "Welcome!")
    }
}

// GOOD — each class has one responsibility
class MemberService(private val repo: MemberRepository) {
    suspend fun register(member: Member) = repo.save(member)
}

class WelcomeNotifier(private val bot: TelegramBot) {
    suspend fun notify(chatId: Long) = bot.sendMessage(chatId, "Welcome!")
}
```

### O — Open/Closed
Extend behaviour via interfaces; never modify existing stable code.

```kotlin
// BAD — every new command requires modifying handleCommand()
fun handleCommand(command: String) {
    when (command) {
        "/start" -> handleStart()
        "/ping"  -> handlePing()
        // adding a new command = editing this function
    }
}

// GOOD — new commands implement the interface; no existing code touched
interface BotCommand {
    val name: String
    suspend fun execute(ctx: CommandContext)
}

class StartCommand : BotCommand {
    override val name = "/start"
    override suspend fun execute(ctx: CommandContext) { /* ... */ }
}
```

### L — Liskov Substitution
Subtypes must be fully substitutable for their base type without breaking behaviour.

```kotlin
// BAD — ReadOnlyGroupRepository violates the contract by throwing on write
interface GroupRepository {
    suspend fun save(group: Group)
    suspend fun findById(id: Long): Group?
}

class ReadOnlyGroupRepository : GroupRepository {
    override suspend fun save(group: Group) = throw UnsupportedOperationException() // LSP violation
    override suspend fun findById(id: Long): Group? = TODO()
}

// GOOD — split into focused interfaces (see ISP below)
interface GroupReader { suspend fun findById(id: Long): Group? }
interface GroupWriter { suspend fun save(group: Group) }
```

### I — Interface Segregation
Keep interfaces small and focused. Don't force implementations to depend on methods they don't use.

```kotlin
// BAD — one fat interface
interface MemberRepository {
    suspend fun save(member: Member)
    suspend fun findById(id: Long): Member?
    suspend fun findAll(): List<Member>
    suspend fun delete(id: Long)
    suspend fun generateReport(): String   // ← unrelated to repository concerns
}

// GOOD — small, cohesive interfaces
interface MemberReader {
    suspend fun findById(id: Long): Member?
    suspend fun findAll(): List<Member>
}

interface MemberWriter {
    suspend fun save(member: Member)
    suspend fun delete(id: Long)
}
```

### D — Dependency Inversion
Depend on abstractions, not concretions. Inject via constructor (Koin handles wiring).

```kotlin
// BAD — hard dependency on concrete implementation
class GroupService {
    private val repo = GroupRepositoryImpl()   // coupled to impl
}

// GOOD — depends on abstraction; Koin injects the concrete impl
class GroupService(private val repo: GroupRepository) {
    suspend fun findGroup(id: Long) = repo.findById(id)
}

// Koin module wiring
val repositoryModule = module {
    single<GroupRepository> { GroupRepositoryImpl(get()) }
    single { GroupService(get()) }
}
```

---

## 2. Functions Must Be Small

Target **5–15 lines**. Hard max ~20 lines. If a function grows beyond that, extract helpers.

```kotlin
// BAD — one long function doing multiple things
suspend fun processNewMember(update: Update) {
    val userId = update.message?.from?.id ?: return
    val chatId = update.message.chat.id
    val username = update.message.from?.username ?: "unknown"
    val existing = memberRepository.findById(userId)
    if (existing != null) {
        bot.sendMessage(chatId, "Already registered")
        return
    }
    val member = Member(id = userId, username = username, chatId = chatId)
    memberRepository.save(member)
    groupMemberRepository.addToDefaultGroup(member)
    bot.sendMessage(chatId, "Welcome, $username!")
    logger.info("Registered $username")
}

// GOOD — each extracted function has one job
suspend fun processNewMember(update: Update) {
    val ctx = extractContext(update) ?: return
    if (isAlreadyRegistered(ctx.userId)) {
        notifyAlreadyRegistered(ctx.chatId)
        return
    }
    val member = registerMember(ctx)
    notifyRegistrationSuccess(member)
}

private suspend fun isAlreadyRegistered(userId: Long) =
    memberRepository.findById(userId) != null

private suspend fun registerMember(ctx: MemberContext): Member {
    val member = Member(id = ctx.userId, username = ctx.username, chatId = ctx.chatId)
    memberRepository.save(member)
    groupMemberRepository.addToDefaultGroup(member)
    return member
}
```

---

## 3. Functions Do ONE Thing

If the name needs "and" — it does too much. Split it.

```kotlin
// BAD
suspend fun validateAndSave(member: Member) { /* ... */ }

// GOOD
suspend fun validate(member: Member): Result<Member> { /* ... */ }
suspend fun save(member: Member) { /* ... */ }
```

---

## 4. Maximum 3 Arguments

More than 3 arguments → introduce a data class or config object.

```kotlin
// BAD — 5 arguments, order is easy to confuse
suspend fun createMember(id: Long, username: String, chatId: Long, groupId: Long, isAdmin: Boolean): Member

// GOOD — bundle into a data class
data class CreateMemberRequest(
    val id: Long,
    val username: String,
    val chatId: Long,
    val groupId: Long,
    val isAdmin: Boolean
)

suspend fun createMember(request: CreateMemberRequest): Member
```

**Exception:** framework/library callbacks (e.g., Exposed column declarations, Ktor routing DSL) may exceed 3 args when required by the API contract. Document such exceptions inline with a comment.

---

## 5. Meaningful Names

| Category   | Rule                                          | Good                          | Bad                  |
|------------|-----------------------------------------------|-------------------------------|----------------------|
| Functions  | verb + noun                                   | `fetchUserById`, `validateGroupAccess` | `getData`, `process` |
| Classes    | noun describing responsibility                | `UserRepository`, `MessageSender` | `Manager`, `Helper` |
| Variables  | descriptive, no abbreviations                 | `userId`, `groupList`         | `uid`, `gl`          |
| Booleans   | `is`, `has`, `can` prefix                     | `isActive`, `hasPermission`, `canJoinGroup` | `active`, `flag`     |

**Banned standalone names:** `data`, `info`, `manager`, `util`, `helper`.
Use qualified names instead: `MemberDataMapper` → `MemberMapper`, `GroupHelper` → `GroupValidator`.

---

## 6. No Side Effects

A function must do exactly what its name says — nothing hidden.

```kotlin
// BAD — name says "find", but it also saves a new member (hidden side effect)
suspend fun findOrCreateMember(id: Long): Member {
    return memberRepository.findById(id) ?: run {
        val member = Member(id = id, username = "unknown", chatId = 0)
        memberRepository.save(member)   // ← side effect not implied by "find"
        member
    }
}

// GOOD — side effect is explicit in the name
suspend fun findMember(id: Long): Member? = memberRepository.findById(id)

suspend fun findOrCreateMember(id: Long): Member =
    findMember(id) ?: createDefaultMember(id)
```

---

## 7. One Level of Abstraction per Function

Each function should operate at a single abstraction level. Don't mix high-level orchestration with low-level details.

```kotlin
// BAD — mixes routing logic with raw SQL detail
suspend fun handleGroupCommand(ctx: CommandContext) {
    val rows = transaction {
        Groups.select { Groups.chatId eq ctx.chatId }.toList()  // low-level SQL
    }
    if (rows.isEmpty()) ctx.bot.sendMessage(ctx.chatId, "No groups found")
    else ctx.bot.sendMessage(ctx.chatId, rows.joinToString { it[Groups.name] })
}

// GOOD — high-level orchestration; details live in the repository
suspend fun handleGroupCommand(ctx: CommandContext) {
    val groups = groupRepository.findByChatId(ctx.chatId)
    val message = formatGroupList(groups)
    ctx.bot.sendMessage(ctx.chatId, message)
}
```

---

## 8. Anti-Patterns to Avoid

### God Class
```kotlin
// BAD — one class owns everything
class BotManager {
    fun handleMessage() { }
    fun registerMember() { }
    fun createGroup() { }
    fun sendNotification() { }
    fun parseCommand() { }
}

// GOOD — split by responsibility
class MessageHandler(...)
class MemberService(...)
class GroupService(...)
class NotificationService(...)
class CommandParser(...)
```

### Flag Arguments
```kotlin
// BAD — Boolean flag changes the entire behaviour
suspend fun sendMessage(chatId: Long, text: String, isMarkdown: Boolean)

// GOOD — two explicit functions
suspend fun sendPlainMessage(chatId: Long, text: String)
suspend fun sendMarkdownMessage(chatId: Long, text: String)
```

### Output Arguments
```kotlin
// BAD — function mutates its argument
fun appendStats(list: MutableList<String>, member: Member) {
    list.add("${member.username}: active")
}

// GOOD — return a value
fun buildStatLine(member: Member): String = "${member.username}: active"
```

### Returning Null When You Should Throw or Use Result
```kotlin
// BAD — null forces all callers to null-check, losing error context
suspend fun getMember(id: Long): Member? = memberRepository.findById(id)

// GOOD — use Result for expected failures, throw for programmer errors
suspend fun getMember(id: Long): Result<Member> =
    memberRepository.findById(id)
        ?.let { Result.success(it) }
        ?: Result.failure(MemberNotFoundException(id))
```

### Deep Nesting (max 2 levels — use guard clauses)
```kotlin
// BAD — 3+ levels of nesting
suspend fun handleMessage(update: Update) {
    if (update.message != null) {
        if (update.message.from != null) {
            if (update.message.text != null) {
                processText(update.message.text)
            }
        }
    }
}

// GOOD — guard clauses flatten nesting
suspend fun handleMessage(update: Update) {
    val message = update.message ?: return
    val from    = message.from   ?: return
    val text    = message.text   ?: return
    processText(text)
}
```

---

## Self-Review Checklist

Run through this before every commit that adds or modifies functions/classes:

- [ ] Each function is ≤ 20 lines; ideally 5–15
- [ ] Each function does exactly ONE thing (no "and" in the name)
- [ ] Each function has ≤ 3 parameters (or uses a data class)
- [ ] All names are meaningful: verb+noun for functions, noun for classes, `is/has/can` for booleans
- [ ] No abbreviations in names (`userId` not `uid`)
- [ ] No banned names: `manager`, `helper`, `util`, `data`, `info` as standalone
- [ ] No hidden side effects — function does what its name says
- [ ] Each function operates at a single abstraction level
- [ ] No flag (Boolean) arguments — split into separate functions
- [ ] No output arguments — return values instead
- [ ] No null returns where `Result` or an exception is more appropriate
- [ ] Nesting depth ≤ 2 levels; guard clauses used where possible
- [ ] No God classes — responsibilities are separated
- [ ] SOLID: each class has one responsibility, depends on abstractions, injected via constructor
