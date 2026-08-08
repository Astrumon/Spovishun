package infrastructure

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
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
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.commands.AddUserToGroupCommand
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.bot.commands.DeleteGroupCommand
import com.ua.astrumon.presentation.bot.commands.EditGroupCommand
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
import com.ua.astrumon.presentation.bot.handler.CallbackHandler
import com.ua.astrumon.presentation.bot.handler.CallbackRouter
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionStore
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.GroupPickerController
import com.ua.astrumon.presentation.controller.GroupSettingsController
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration.Companion.hours

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationTest {
    // Real DB-backed repositories
    private val memberRepo = MemberRepositoryImpl()
    private val memberChatRepo = MemberChatRepositoryImpl()
    private val chatRepo = ChatRepositoryImpl()
    private val groupRepo = GroupRepositoryImpl()
    private val groupMemberRepo = GroupMemberRepositoryImpl()
    private val birthdayGreetingRepo = BirthdayGreetingRepositoryImpl()

    // Private: subclasses that need the birthday service build their own (see BirthdayCommandIntegrationTest);
    // this instance only backs RegistrationController's `/register $b DD.MM` path.
    private val birthdayService = BirthdayService(memberRepo, memberChatRepo, birthdayGreetingRepo)

    // Real services
    protected lateinit var memberService: MemberService
    protected lateinit var chatService: ChatService
    protected lateinit var groupService: GroupService
    protected lateinit var autoRegisterService: AutoRegisterService

    // Real provider over the real ChatService, so language resolution hits the database like in production
    protected lateinit var messagesProvider: BotMessagesProvider

    // Caches wired into AutoRegisterService — recreated per test so cases can inspect/evict entries
    protected lateinit var userCache: UserCache
    protected lateinit var chatCache: ChatCache

    // Telegram API mocks (the only mocked boundary)
    protected lateinit var bot: Bot
    protected lateinit var botAdminUtils: BotAdminUtils
    protected lateinit var autoRegistrar: MemberAutoRegistrar

    // Controllers — real
    protected lateinit var groupController: GroupController
    protected lateinit var groupPickerController: GroupPickerController
    protected lateinit var groupSettingsController: GroupSettingsController
    protected lateinit var membersController: MembersController
    protected lateinit var registrationController: RegistrationController
    protected lateinit var pingController: PingController
    protected lateinit var readinessSessionStore: ReadinessSessionStore
    protected lateinit var readinessSessionRunner: ReadinessSessionRunner
    private lateinit var readinessScope: CoroutineScope
    protected lateinit var randomController: RandomController

    // Commands — real
    protected lateinit var startCommand: StartCommand
    protected lateinit var registerCommand: RegisterCommand
    protected lateinit var membersCommand: MembersCommand
    protected lateinit var grantRoleCommand: GrantRoleCommand
    protected lateinit var showGroupsCommand: ShowGroupsCommand
    protected lateinit var newGroupCommand: NewGroupCommand
    protected lateinit var deleteGroupCommand: DeleteGroupCommand
    protected lateinit var editGroupCommand: EditGroupCommand
    protected lateinit var addUserToGroupCommand: AddUserToGroupCommand
    protected lateinit var removeUserFromGroupCommand: RemoveUserFromGroupCommand
    protected lateinit var pingAllCommand: PingAllCommand
    protected lateinit var pingGroupCommand: PingGroupCommand
    protected lateinit var randomCommand: RandomCommand
    protected lateinit var messageHandler: MessageHandler

    protected lateinit var cleaner: TestDatabaseCleaner

    protected val testChatId = -1001234567890L
    protected val testUserId = 111L
    protected val testUsername = "testuser"
    protected val testFirstName = "Test"
    protected val testAdminId = 222L
    protected val testAdminUsername = "adminuser"

    @BeforeAll
    fun initDatabase() {
        assumeTrue(IntegrationDbConfig.isConfigured, "E2E_DATABASE_URL not set — skipping integration tests")
        val databaseUrl = requireNotNull(IntegrationDbConfig.databaseUrl) { "E2E_DATABASE_URL must be set" }
        TestDatabaseFactory.initialize(
            url = databaseUrl,
            driver = IntegrationDbConfig.databaseDriver,
            username = IntegrationDbConfig.databaseUsername,
            password = IntegrationDbConfig.databasePassword,
            poolSize = IntegrationDbConfig.databasePoolSize,
        )
        cleaner = TestDatabaseCleaner(databaseUrl)
    }

    @BeforeTest
    fun setUpBase() {
        assumeTrue(IntegrationDbConfig.isConfigured, "E2E_DATABASE_URL not set — skipping integration tests")
        // Pre-clean as well as post-clean: guarantees a clean slate even if a prior run left
        // stale rows for testChatId (e.g. a crashed test that never reached tearDown). Without
        // this, the first test to run could observe leaked members and pick the wrong one.
        runBlocking { cleaner.cleanupByChatId(testChatId) }
        clearAllMocks()

        initServices()
        initTelegramMocks()
        initControllers()
        initReadiness()
        initCommands()
    }

    private fun initServices() {
        memberService = MemberService(memberRepo, memberChatRepo)
        chatService = ChatService(chatRepo)
        groupService = GroupService(groupRepo, groupMemberRepo)
        userCache = UserCache()
        chatCache = ChatCache()
        autoRegisterService = AutoRegisterService(memberService, chatService, userCache, chatCache)
        messagesProvider = BotMessagesProvider(chatService)
    }

    private fun initTelegramMocks() {
        bot = mockk(relaxed = true)
        botAdminUtils = mockk()
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
        every { botAdminUtils.isUserAdmin(any(), any(), any()) } returns false
        autoRegistrar = MemberAutoRegistrar(autoRegisterService, botAdminUtils)
        every { bot.getChat(any()) } returns
            TelegramBotResult.Success(
                Chat(id = testChatId, type = "supergroup"),
            )
        // A relaxed mock erases the generic and hands back an Object, which the readiness runner
        // cannot read a messageId from. Return a real result so the poll registers a session.
        every { bot.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TelegramBotResult.Success(
                Message(
                    messageId = SENT_MESSAGE_ID,
                    date = 0L,
                    chat = Chat(id = testChatId, type = "supergroup"),
                ),
            )
    }

    private fun initControllers() {
        groupController = GroupController(groupService, memberService, messagesProvider)
        groupPickerController = GroupPickerController(groupService, memberService, messagesProvider)
        groupSettingsController = GroupSettingsController(groupService, memberService, messagesProvider)
        membersController = MembersController(memberService, messagesProvider)
        registrationController = RegistrationController(autoRegisterService, birthdayService, messagesProvider)
        pingController = PingController(memberService, groupService, chatService, messagesProvider)
        randomController = RandomController(memberService, groupService, messagesProvider)
    }

    /**
     * A real store and runner, so the readiness branch is exercised end to end. The TTL is long
     * enough that no poll expires mid-test; [readinessScope] is cancelled in tearDown so the pending
     * expiry coroutines do not leak between tests.
     */
    private fun initReadiness() {
        readinessSessionStore = ReadinessSessionStore()
        readinessScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        readinessSessionRunner = ReadinessSessionRunner(
            readinessSessionStore,
            readinessScope,
            object : ReadinessConfig {
                override val readinessTtl = READINESS_TEST_TTL
            },
        )
    }

    private fun initCommands() {
        startCommand = StartCommand(registrationController, botAdminUtils, messagesProvider)
        registerCommand = RegisterCommand(registrationController, botAdminUtils, messagesProvider)
        membersCommand = MembersCommand(membersController, messagesProvider)
        grantRoleCommand = GrantRoleCommand(groupController, groupPickerController, messagesProvider)
        showGroupsCommand = ShowGroupsCommand(groupController, messagesProvider)
        newGroupCommand = NewGroupCommand(groupController, messagesProvider)
        deleteGroupCommand = DeleteGroupCommand(groupController, groupPickerController, messagesProvider)
        editGroupCommand = EditGroupCommand(groupSettingsController, messagesProvider)
        addUserToGroupCommand = AddUserToGroupCommand(groupController, groupPickerController, messagesProvider)
        removeUserFromGroupCommand = RemoveUserFromGroupCommand(groupController, groupPickerController, messagesProvider)
        pingAllCommand = PingAllCommand(pingController, readinessSessionRunner, messagesProvider)
        pingGroupCommand = PingGroupCommand(pingController, readinessSessionRunner, messagesProvider)
        randomCommand = RandomCommand(randomController, messagesProvider)
        messageHandler = MessageHandler(autoRegistrar, mockk(relaxed = true))
    }

    /**
     * Runs a command the way production does — through [CommandRegistry], which wraps every entry in
     * the chat-log and auto-register decorators (spovishun-172). Calling `command.execute` directly
     * skips both, so any test that depends on the caller being registered must go through here.
     */
    protected suspend fun dispatch(
        command: BotCommand,
        update: Update,
    ) = CommandRegistry(listOf(command), autoRegistrar).commands.first().execute(bot, update)

    /**
     * Routes a callback the way production does. Since spovishun-172 a handler receives a parsed
     * [com.ua.astrumon.presentation.bot.handler.CallbackContext] rather than an `Update`, and the
     * acknowledgement is the router's — so driving a handler directly no longer exercises the press.
     */
    protected suspend fun dispatchCallback(
        handler: CallbackHandler,
        update: Update,
    ) = CallbackRouter(listOf(handler), messagesProvider, autoRegistrar).route(bot, update)

    @AfterTest
    fun tearDown() {
        if (::readinessScope.isInitialized) readinessScope.cancel()
        if (!IntegrationDbConfig.isConfigured) return
        try {
            runBlocking { cleaner.cleanupByChatId(testChatId) }
        } catch (e: Exception) {
            // prevent cleanup failure from masking the actual test result
        }
    }

    protected fun buildUpdate(
        text: String,
        userId: Long = testUserId,
        username: String = testUsername,
        firstName: String = testFirstName,
        chatId: Long = testChatId,
        chatType: String = "supergroup",
    ): Update {
        val user = User(id = userId, isBot = false, firstName = firstName, username = username)
        val chat = Chat(id = chatId, type = chatType)
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = text)
        return Update(updateId = 1L, message = message)
    }

    /**
     * Builds a callback-query [Update] for the routing chain (spovishun-161).
     *
     * `:bot` has an equivalent factory in its own `test` source set, but that output is not on
     * `:app`'s classpath — only `:app`'s `main` and `test` outputs are.
     */
    protected fun buildCallbackUpdate(
        data: String,
        clickerId: Long = testUserId,
        chatId: Long = testChatId,
        messageId: Long = 5L,
        callbackId: String = "cb",
    ): Update {
        val user = User(id = clickerId, isBot = false, firstName = testFirstName, username = testUsername)
        val chat = Chat(id = chatId, type = "supergroup")
        val message = Message(messageId = messageId, date = 0L, chat = chat)
        val callbackQuery = CallbackQuery(
            id = callbackId,
            from = user,
            message = message,
            inlineMessageId = null,
            data = data,
            chatInstance = "i",
        )
        return Update(updateId = 1L, callbackQuery = callbackQuery)
    }

    protected suspend fun registerMember(
        userId: Long = testUserId,
        username: String = testUsername,
        firstName: String = testFirstName,
        chatId: Long = testChatId,
        role: MemberRole = MemberRole.MEMBER,
    ): MemberWithChat = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { role }).getOrThrow()

    /** Readiness is on by default; tests that assert the classic plain ping opt out through these. */
    protected suspend fun disableChatReadiness(chatId: Long = testChatId) {
        chatService.setReadinessEnabled(chatId, enabled = false).getOrThrow()
    }

    protected suspend fun disableGroupReadiness(
        key: String,
        chatId: Long = testChatId,
    ) {
        groupService.setReadinessEnabled(chatId, key, enabled = false).getOrThrow()
    }

    private companion object {
        /** Far longer than any test takes, so a poll never expires while assertions run. */
        val READINESS_TEST_TTL = 1.hours

        /** Message id the mocked Telegram reports for every send — the key a readiness poll opens on. */
        const val SENT_MESSAGE_ID = 900L
    }
}
