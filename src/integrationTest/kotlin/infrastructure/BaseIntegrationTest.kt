package infrastructure

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.data.db.repository.ChatRepositoryImpl
import com.ua.astrumon.data.db.repository.GroupMemberRepositoryImpl
import com.ua.astrumon.data.db.repository.GroupRepositoryImpl
import com.ua.astrumon.data.db.repository.MemberChatRepositoryImpl
import com.ua.astrumon.data.db.repository.MemberRepositoryImpl
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.ChatService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.MemberService
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
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationTest {

    // Real DB-backed repositories
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

    // Telegram API mocks (the only mocked boundary)
    protected lateinit var bot: Bot
    protected lateinit var botAdminUtils: BotAdminUtils

    // Controllers — real
    protected lateinit var groupController: GroupController
    protected lateinit var membersController: MembersController
    protected lateinit var registrationController: RegistrationController
    protected lateinit var pingController: PingController

    // Commands — real
    protected lateinit var startCommand: StartCommand
    protected lateinit var registerCommand: RegisterCommand
    protected lateinit var membersCommand: MembersCommand
    protected lateinit var grantRoleCommand: GrantRoleCommand
    protected lateinit var showGroupsCommand: ShowGroupsCommand
    protected lateinit var newGroupCommand: NewGroupCommand
    protected lateinit var deleteGroupCommand: DeleteGroupCommand
    protected lateinit var addUserToGroupCommand: AddUserToGroupCommand
    protected lateinit var removeUserFromGroupCommand: RemoveUserFromGroupCommand
    protected lateinit var pingAllCommand: PingAllCommand
    protected lateinit var pingGroupCommand: PingGroupCommand
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
        TestDatabaseFactory.initialize(
            url = IntegrationDbConfig.databaseUrl!!,
            driver = IntegrationDbConfig.databaseDriver,
            username = IntegrationDbConfig.databaseUsername,
            password = IntegrationDbConfig.databasePassword,
            poolSize = IntegrationDbConfig.databasePoolSize,
        )
        cleaner = TestDatabaseCleaner(IntegrationDbConfig.databaseUrl!!)
    }

    @BeforeTest
    fun setUpBase() {
        assumeTrue(IntegrationDbConfig.isConfigured, "E2E_DATABASE_URL not set — skipping integration tests")
        clearAllMocks()

        memberService = MemberService(memberRepo, memberChatRepo)
        chatService = ChatService(chatRepo)
        groupService = GroupService(groupRepo, groupMemberRepo)
        autoRegisterService = AutoRegisterService(memberService, chatService)

        bot = mockk(relaxed = true)
        botAdminUtils = mockk()
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
        every { botAdminUtils.isUserAdmin(any(), any(), any()) } returns false
        every { bot.getChat(any()) } returns com.github.kotlintelegrambot.types.TelegramBotResult.Success(
            Chat(id = testChatId, type = "supergroup")
        )

        groupController = GroupController(groupService, memberService, autoRegisterService)
        membersController = MembersController(memberService, autoRegisterService)
        registrationController = RegistrationController(autoRegisterService)
        pingController = PingController(memberService, groupService, autoRegisterService)

        startCommand = StartCommand(registrationController, botAdminUtils)
        registerCommand = RegisterCommand(registrationController, botAdminUtils)
        membersCommand = MembersCommand(membersController, botAdminUtils)
        grantRoleCommand = GrantRoleCommand(groupController)
        showGroupsCommand = ShowGroupsCommand(groupController, botAdminUtils)
        newGroupCommand = NewGroupCommand(groupController)
        deleteGroupCommand = DeleteGroupCommand(groupController)
        addUserToGroupCommand = AddUserToGroupCommand(groupController)
        removeUserFromGroupCommand = RemoveUserFromGroupCommand(groupController)
        pingAllCommand = PingAllCommand(pingController, botAdminUtils)
        pingGroupCommand = PingGroupCommand(pingController, botAdminUtils)
        messageHandler = MessageHandler(autoRegisterService, botAdminUtils)
    }

    @AfterTest
    fun tearDown() {
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
        chatType: String = "supergroup"
    ): Update {
        val user = User(id = userId, isBot = false, firstName = firstName, username = username)
        val chat = Chat(id = chatId, type = chatType)
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = text)
        return Update(updateId = 1L, message = message)
    }

    protected suspend fun registerMember(
        userId: Long = testUserId,
        username: String = testUsername,
        firstName: String = testFirstName,
        chatId: Long = testChatId,
        role: MemberRole = MemberRole.MEMBER
    ): MemberWithChat = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, role).getOrThrow()
}
