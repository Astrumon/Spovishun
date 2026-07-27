package infrastructure

import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.data.bot.repository.ChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupMemberRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberRepositoryImpl
import com.ua.astrumon.domain.bot.cache.ChatCache
import com.ua.astrumon.domain.bot.cache.UserCache
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.TelegramBot
import com.ua.astrumon.presentation.bot.commands.AddUserToGroupCommand
import com.ua.astrumon.presentation.bot.commands.DeleteGroupCommand
import com.ua.astrumon.presentation.bot.commands.GrantRoleCommand
import com.ua.astrumon.presentation.bot.commands.MembersCommand
import com.ua.astrumon.presentation.bot.commands.NewGroupCommand
import com.ua.astrumon.presentation.bot.commands.PingAllCommand
import com.ua.astrumon.presentation.bot.commands.PingGroupCommand
import com.ua.astrumon.presentation.bot.commands.RegisterCommand
import com.ua.astrumon.presentation.bot.commands.RemoveUserFromGroupCommand
import com.ua.astrumon.presentation.bot.commands.ShowGroupsCommand
import com.ua.astrumon.presentation.bot.commands.StartCommand
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base class for E2E tests.
 *
 * Architecture:
 * - Commands are dispatched directly (bypasses the Telegram bot-to-bot message limitation).
 * - The real [com.github.kotlintelegrambot.Bot] instance makes real Telegram API calls:
 *   bot.sendMessage() → actual delivery to the test group
 *   bot.getChatMember() → real admin detection via BotAdminUtils
 * - Uses real [*RepositoryImpl] classes backed by the dev DB (requires E2E_DATABASE_* env vars).
 * - [TestDatabaseCleaner] deletes all entities scoped to [testChatId] after each test,
 *   even on failure (JUnit guarantees [@AfterTest] execution).
 * - Chat cleanup runs once after all tests in the class complete (@AfterAll).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseE2ETest {
    // Real Telegram bot (makes actual API calls: sendMessage, getChatMember, etc.)
    protected lateinit var mainBot: com.github.kotlintelegrambot.Bot

    // Helper bot info (used only to obtain helperBotId for synthetic updates)
    private lateinit var helperBot: TelegramHelperBot
    protected var helperBotId: Long = 0L

    // Accumulates message ID ranges across all tests; deleted once in @AfterAll
    private val messagesToCleanup = mutableListOf<LongRange>()

    // Real DB-backed repositories (stateless — safe to reuse across tests)
    private val memberRepo = MemberRepositoryImpl()
    private val memberChatRepo = MemberChatRepositoryImpl()
    private val chatRepo = ChatRepositoryImpl()
    private val groupRepo = GroupRepositoryImpl()
    private val groupMemberRepo = GroupMemberRepositoryImpl()

    // Real services
    protected lateinit var memberService: MemberService
    protected lateinit var chatService: ChatService
    protected lateinit var groupService: GroupService
    protected lateinit var autoRegisterService: AutoRegisterService

    // Caches wired into AutoRegisterService — recreated per test so cases can inspect/evict entries
    protected lateinit var userCache: UserCache
    protected lateinit var chatCache: ChatCache

    // Command handlers (for direct dispatch)
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var messageHandler: MessageHandler

    protected lateinit var cleaner: TestDatabaseCleaner

    protected val testChatId: Long get() = E2EConfig.testChatId!!

    @BeforeAll
    fun initDatabase() {
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
    }

    @BeforeTest
    fun setUpE2E() {
        assumeTrue(E2EConfig.isConfigured && E2EDbConfig.isConfigured, "E2E env vars not set — skipping e2e tests")

        runBlocking {
            val helperBotToken = E2EConfig.helperBotToken!!
            helperBot = TelegramHelperBot(helperBotToken, testChatId)
            helperBotId = helperBot.getBotId(helperBotToken)
        }

        memberService = MemberService(memberRepo, memberChatRepo)
        chatService = ChatService(chatRepo)
        groupService = GroupService(groupRepo, groupMemberRepo)
        userCache = UserCache()
        chatCache = ChatCache()
        autoRegisterService = AutoRegisterService(memberService, chatService, userCache, chatCache)

        val botAdminUtils = BotAdminUtils()

        val groupController = GroupController(groupService, memberService, autoRegisterService)
        val membersController = MembersController(memberService, autoRegisterService)
        val registrationController = RegistrationController(autoRegisterService)
        val pingController = PingController(memberService, groupService, autoRegisterService)

        messageHandler = MessageHandler(autoRegisterService, botAdminUtils, mockk(relaxed = true))
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
                PingAllCommand(pingController, botAdminUtils),
                PingGroupCommand(pingController, botAdminUtils),
            ),
        )

        val telegramBot = TelegramBot(commandRegistry, messageHandler, mockk(relaxed = true), mockk())
        mainBot = telegramBot.create(E2EConfig.mainBotToken!!)

        // Send a sentinel message and enqueue its range for bulk cleanup in @AfterAll
        runBlocking {
            val result = mainBot.sendMessage(chatId = ChatId.fromId(testChatId), text = "🧪")
            val sentinelId = result.getOrNull()?.messageId
            if (sentinelId != null) {
                messagesToCleanup.add(sentinelId..sentinelId + 30L)
            }
        }
    }

    @AfterTest
    fun tearDownE2E() {
        if (!E2EConfig.isConfigured || !E2EDbConfig.isConfigured) return
        runBlocking { cleaner.cleanupByChatId(testChatId) }
        helperBot.close()
    }

    @AfterAll
    fun cleanupTestChat() {
        if (!E2EConfig.isConfigured || !::mainBot.isInitialized || messagesToCleanup.isEmpty()) return
        for (range in messagesToCleanup) {
            for (id in range) {
                mainBot.deleteMessage(chatId = ChatId.fromId(testChatId), messageId = id)
            }
        }
        messagesToCleanup.clear()
        TestDatabaseFactory.shutdown()
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

    protected suspend fun dispatchCommand(
        command: String,
        update: Update,
    ) {
        val name = command.trimStart('/').split(" ").firstOrNull() ?: return
        val cmd = commandRegistry.commands.find { it.name == name }
        if (cmd != null) {
            cmd.execute(mainBot, update)
        } else {
            messageHandler.handleIncomingMessage(mainBot, update)
        }
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
}
