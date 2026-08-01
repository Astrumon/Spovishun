package infrastructure

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.data.bot.releasenotes.ReleaseNotesRepositoryImpl
import com.ua.astrumon.data.bot.repository.BirthdayGreetingRepositoryImpl
import com.ua.astrumon.data.bot.repository.ChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupMemberRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberRepositoryImpl
import com.ua.astrumon.domain.bot.cache.ChatCache
import com.ua.astrumon.domain.bot.cache.UserCache
import com.ua.astrumon.domain.bot.config.ReadinessConfig
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.commands.AddUserToGroupCommand
import com.ua.astrumon.presentation.bot.commands.BirthdayCommand
import com.ua.astrumon.presentation.bot.commands.DeleteGroupCommand
import com.ua.astrumon.presentation.bot.commands.GrantRoleCommand
import com.ua.astrumon.presentation.bot.commands.MembersCommand
import com.ua.astrumon.presentation.bot.commands.NewGroupCommand
import com.ua.astrumon.presentation.bot.commands.PingAllCommand
import com.ua.astrumon.presentation.bot.commands.PingGroupCommand
import com.ua.astrumon.presentation.bot.commands.RandomCommand
import com.ua.astrumon.presentation.bot.commands.RegisterCommand
import com.ua.astrumon.presentation.bot.commands.RemoveUserFromGroupCommand
import com.ua.astrumon.presentation.bot.commands.ShowGroupsCommand
import com.ua.astrumon.presentation.bot.commands.StartCommand
import com.ua.astrumon.presentation.bot.commands.WhatsNewCommand
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionStore
import com.ua.astrumon.presentation.controller.BirthdayController
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for E2E tests — the only layer that talks to the real Telegram API.
 *
 * ## What "e2e" means here
 * Commands are dispatched directly rather than through long polling: Telegram does not deliver one
 * bot's messages to another, so a helper bot cannot type `/ping` into the chat and have the bot
 * under test see it. What *is* real is the outbound half — every reply is genuinely sent to a real
 * Telegram chat, and [BotAdminUtils] resolves roles through a real `getChatMember`.
 *
 * ## Reading back what Telegram actually received
 * [mainBot] is a MockK spy over a real [com.github.kotlintelegrambot.Bot]. It stubs nothing: every
 * `sendMessage` call runs for real and its `TelegramBotResult<Message>` is recorded before being
 * returned. That response is Telegram's own view of the delivered message — text after HTML
 * parsing, the entities it produced, the inline keyboard it accepted — which is precisely the
 * evidence integration tests (mocked `Bot`) cannot produce. Tests reach it via
 * [dispatchExpectingReply] / [expectingReply].
 *
 * ## State hygiene
 * - The DB is cleaned for [testChatId] **before** and **after** every test, so a crashed run cannot
 *   leak rows into the next one.
 * - Every message the bot actually posted is remembered by id and deleted in [cleanupTestChat].
 *   No id guessing: only ids Telegram itself returned are touched.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseE2ETest {
    /**
     * Real Telegram bot under a recording spy — see the class KDoc. Passed to commands as-is, so
     * everything they send goes to the live chat.
     */
    protected lateinit var mainBot: com.github.kotlintelegrambot.Bot

    /** Real Telegram user id of the helper bot; the synthetic updates are sent as this user. */
    protected var helperBotId: Long = 0L

    // Messages Telegram confirmed for the current test — reset in @BeforeTest.
    private val delivered = mutableListOf<Message>()

    // Sends Telegram rejected during the current test, kept so a missing reply can explain itself.
    private val sendFailures = mutableListOf<String>()

    // Ids of every message posted across the whole class; deleted once in @AfterAll.
    private val postedMessageIds = mutableListOf<Long>()

    // Real DB-backed repositories (stateless — safe to reuse across tests)
    private val memberRepo = MemberRepositoryImpl()
    private val memberChatRepo = MemberChatRepositoryImpl()
    private val chatRepo = ChatRepositoryImpl()
    private val groupRepo = GroupRepositoryImpl()
    private val groupMemberRepo = GroupMemberRepositoryImpl()
    private val birthdayGreetingRepo = BirthdayGreetingRepositoryImpl()
    private val releaseNotesRepo = ReleaseNotesRepositoryImpl()

    // Real services. Only what tests actually reach for is exposed — the harness keeps the rest
    // private so it cannot quietly grow another unused surface.
    protected lateinit var memberService: MemberService
    protected lateinit var groupService: GroupService

    /** Real admin detection through the live Telegram API — never mocked at this layer. */
    protected lateinit var botAdminUtils: BotAdminUtils

    /** Exposed for the callback handlers, which tests construct directly. */
    protected lateinit var pingController: PingController

    /** Same reason as [pingController] — the ping commands and handler both need it. */
    protected lateinit var readinessSessionRunner: ReadinessSessionRunner

    private lateinit var readinessScope: CoroutineScope
    private lateinit var chatService: ChatService
    private lateinit var autoRegisterService: AutoRegisterService
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var cleaner: TestDatabaseCleaner

    protected val testChatId: Long get() = E2EConfig.testChatId!!

    @BeforeAll
    fun initHarness() {
        assumeTrue(E2EConfig.isConfigured && E2EDbConfig.isConfigured, "E2E env vars not set — skipping e2e tests")
        val databaseUrl = requireNotNull(E2EDbConfig.databaseUrl) { "E2E_DATABASE_URL must be set" }
        TestDatabaseFactory.initialize(
            url = databaseUrl,
            driver = E2EDbConfig.databaseDriver,
            username = E2EDbConfig.databaseUsername,
            password = E2EDbConfig.databasePassword,
            poolSize = E2EDbConfig.databasePoolSize,
        )
        cleaner = TestDatabaseCleaner(databaseUrl)

        // The helper bot id is a constant of the token — resolve it once per class, not per test.
        val helperBot = TelegramHelperBot(E2EConfig.helperBotToken!!)
        helperBotId = try {
            runBlocking { helperBot.resolveBotId() }
        } finally {
            helperBot.close()
        }
    }

    @BeforeTest
    fun setUpE2E() {
        assumeTrue(E2EConfig.isConfigured && E2EDbConfig.isConfigured, "E2E env vars not set — skipping e2e tests")

        // Pre-clean as well as post-clean: a test that crashed before its @AfterTest must not leak
        // rows into this one. Mirrors BaseIntegrationTest.
        runBlocking { cleaner.cleanupByChatId(testChatId) }
        delivered.clear()
        sendFailures.clear()

        initServices()
        initBot()
        initCommands()
    }

    private fun initServices() {
        memberService = MemberService(memberRepo, memberChatRepo)
        chatService = ChatService(chatRepo)
        groupService = GroupService(groupRepo, groupMemberRepo)
        // Caches are recreated per test so nothing carries over between cases.
        autoRegisterService = AutoRegisterService(memberService, chatService, UserCache(), ChatCache())
        botAdminUtils = BotAdminUtils()
        // TTL far beyond any test run, so no poll expires mid-assertion; the scope is cancelled in tearDown.
        readinessScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        readinessSessionRunner = ReadinessSessionRunner(
            ReadinessSessionStore(),
            readinessScope,
            object : ReadinessConfig {
                override val readinessTtl = READINESS_TEST_TTL
            },
        )
    }

    /**
     * Wraps the real bot in a spy that records what Telegram returned for every `sendMessage`.
     * `callOriginal()` keeps the call fully real — the spy observes, it never substitutes.
     */
    private fun initBot() {
        // Built straight from the library DSL: the tests dispatch commands themselves, so
        // TelegramBot's polling dispatcher plays no part here and wiring it would only mislead.
        mainBot = spyk(bot { token = E2EConfig.mainBotToken!! })
        every {
            mainBot.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            var result = callOriginal()
            var attempts = 0
            // Telegram caps a group at roughly twenty messages a minute and answers 429 with the
            // exact number of seconds to wait. Honouring it is protocol compliance, not a guessed
            // delay: back-to-back suite runs otherwise start dropping replies, which is precisely
            // what went unnoticed while nothing asserted delivery (spovishun-160).
            while (attempts < MAX_RATE_LIMIT_RETRIES) {
                val retryAfter = result.retryAfterSeconds() ?: break
                runBlocking { delay(retryAfter.seconds) }
                result = callOriginal()
                attempts++
            }
            result.fold(
                { message ->
                    delivered.add(message)
                    postedMessageIds.add(message.messageId)
                },
                // A rejected send is the failure mode this layer exists to catch. Production code
                // ignores the result, so without this the message simply never appears and the test
                // has no way to say why.
                { error -> sendFailures.add(error.toString()) },
            )
            result
        }
    }

    private fun initCommands() {
        val groupController = GroupController(groupService, memberService, autoRegisterService)
        val membersController = MembersController(memberService, autoRegisterService)
        val birthdayService = BirthdayService(memberRepo, memberChatRepo, birthdayGreetingRepo)
        val registrationController = RegistrationController(autoRegisterService, birthdayService)
        val randomController = RandomController(memberService, groupService, autoRegisterService)
        val birthdayController = BirthdayController(birthdayService, memberService)
        val whatsNewController = WhatsNewController(ReleaseNotesService(releaseNotesRepo), chatService, memberService)
        pingController = PingController(memberService, groupService, chatService, autoRegisterService)

        // Kept in lockstep with :app di/PresentationModule — a command missing here would make
        // dispatch() fail loudly rather than silently prove nothing.
        commandRegistry = CommandRegistry(
            listOf(
                StartCommand(registrationController, botAdminUtils),
                RegisterCommand(registrationController, botAdminUtils),
                MembersCommand(membersController, botAdminUtils),
                GrantRoleCommand(groupController),
                ShowGroupsCommand(groupController, botAdminUtils),
                NewGroupCommand(groupController),
                DeleteGroupCommand(groupController),
                AddUserToGroupCommand(groupController),
                RemoveUserFromGroupCommand(groupController),
                PingAllCommand(pingController, botAdminUtils, readinessSessionRunner),
                PingGroupCommand(pingController, botAdminUtils, readinessSessionRunner),
                BirthdayCommand(birthdayController),
                WhatsNewCommand(whatsNewController),
                RandomCommand(randomController, botAdminUtils),
            ),
        )
    }

    @AfterTest
    fun tearDownE2E() {
        if (::readinessScope.isInitialized) readinessScope.cancel()
        if (!E2EConfig.isConfigured || !E2EDbConfig.isConfigured) return
        runBlocking { cleaner.cleanupByChatId(testChatId) }
    }

    @AfterAll
    fun cleanupTestChat() {
        if (!E2EConfig.isConfigured || !::mainBot.isInitialized) return
        // Only ids Telegram actually handed back — a message already removed in place by a picker
        // simply answers "message to delete not found", which is harmless. Deletes are best-effort
        // and deliberately not retried on a 429: an undeleted message is cosmetic, and burning the
        // rate-limit budget on cleanup would starve the next class's actual sends.
        for (id in postedMessageIds) {
            mainBot.deleteMessage(chatId = ChatId.fromId(testChatId), messageId = id)
        }
        postedMessageIds.clear()
    }

    protected fun buildUpdate(
        text: String,
        userId: Long = helperBotId,
        username: String = "helper_bot",
        firstName: String = "HelperBot",
        chatId: Long = testChatId,
        chatType: String = "supergroup",
    ): Update {
        val user = User(id = userId, isBot = true, firstName = firstName, username = username)
        val chat = Chat(id = chatId, type = chatType)
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = text)
        return Update(updateId = 1L, message = message)
    }

    protected fun dispatch(command: String) {
        runBlocking { dispatchCommand(command, buildUpdate(text = command)) }
    }

    /** Dispatches [command] and returns the message Telegram confirmed for it. */
    protected fun dispatchExpectingReply(command: String): Message = expectingReply("'$command'") { dispatch(command) }

    /**
     * Runs [block] and returns the **first** message Telegram confirmed for it.
     *
     * First, not last: a command may send more than one message (`/start` adds an invitation in a
     * plain `group`), and the reply a test reasons about is the one that came out of the action, not
     * whatever happened to land afterwards.
     *
     * Fails when nothing was delivered — silence is what a broken reply looks like from the outside,
     * so it must never read as a pass. When Telegram rejected the send, its own wording is reported
     * rather than a bare "no message".
     */
    protected fun expectingReply(
        what: String,
        block: () -> Unit,
    ): Message {
        val before = delivered.size
        val failuresBefore = sendFailures.size
        block()
        check(delivered.size > before) {
            val rejected = sendFailures.drop(failuresBefore)
            if (rejected.isEmpty()) {
                "Expected $what to deliver a message to Telegram, but it never called sendMessage"
            } else {
                "Telegram rejected the reply to $what: ${rejected.joinToString("; ")}"
            }
        }
        return delivered[before]
    }

    /**
     * Resolves the command by name and executes it.
     *
     * An unknown name is an error, not a fallback: silently routing a mistyped command into
     * `MessageHandler` used to let such a test pass green while proving nothing (spovishun-160).
     * Plain, non-command messages are `MessageHandler`'s job and are covered in
     * `MessageHandlerIntegrationTest` — they need no Telegram round trip.
     */
    protected suspend fun dispatchCommand(
        command: String,
        update: Update,
    ) {
        val name = command.trimStart('/').substringBefore(' ')
        val cmd = commandRegistry.commands.find { it.name == name }
            ?: error("No command named '$name' is registered — the e2e registry is out of sync with PresentationModule")
        cmd.execute(mainBot, update)
    }

    protected fun registerMember(
        userId: Long,
        username: String,
        firstName: String,
        role: MemberRole = MemberRole.MEMBER,
    ): MemberWithChat = runBlocking {
        autoRegisterService.ensureUserRegistered(testChatId, userId, username, firstName, role).getOrThrow()
    }

    protected fun allMembers() = runBlocking { memberService.getAllMembersInChat(testChatId).getOrThrow() }

    protected fun allGroups() = runBlocking { groupService.getAllGroupsWithMembers(testChatId).getOrThrow() }

    /** Seconds Telegram asked us to wait, or null when this is not a rate-limit rejection. */
    private fun TelegramBotResult<Message>.retryAfterSeconds(): Long? = fold({ null }, { error ->
        val api = error as? TelegramBotResult.Error.TelegramApi
        if (api == null || api.errorCode != TOO_MANY_REQUESTS) {
            null
        } else {
            RETRY_AFTER
                .find(api.description)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        }
    })

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val MAX_RATE_LIMIT_RETRIES = 2
        val RETRY_AFTER = Regex("""retry after (\d+)""")

        /** Far longer than any test takes, so a readiness poll never expires mid-assertion. */
        val READINESS_TEST_TTL = 1.hours
    }
}
