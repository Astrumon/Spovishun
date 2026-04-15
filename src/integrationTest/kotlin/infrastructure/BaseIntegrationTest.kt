package infrastructure

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.data.memory.repository.ChatRepositoryMockImpl
import com.ua.astrumon.data.memory.repository.GroupMemberRepositoryMockImpl
import com.ua.astrumon.data.memory.repository.GroupRepositoryMockImpl
import com.ua.astrumon.data.memory.repository.MemberChatRepositoryMockImpl
import com.ua.astrumon.data.memory.repository.MemberRepositoryMockImpl
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
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest

abstract class BaseIntegrationTest {

    // Repos — fresh per test, no shared state
    protected lateinit var memberRepo: MemberRepositoryMockImpl
    protected lateinit var memberChatRepo: MemberChatRepositoryMockImpl
    protected lateinit var chatRepo: ChatRepositoryMockImpl
    protected lateinit var groupRepo: GroupRepositoryMockImpl
    protected lateinit var groupMemberRepo: GroupMemberRepositoryMockImpl

    // Services — real, wired with real repos
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

    // Test constants
    protected val testChatId = -1001234567890L
    protected val testUserId = 111L
    protected val testUsername = "testuser"
    protected val testFirstName = "Test"
    protected val testAdminId = 222L
    protected val testAdminUsername = "adminuser"

    @BeforeTest
    fun setUpBase() {
        clearAllMocks()

        // Fresh repos each test — memberChatRepo must be initialized before memberRepo
        memberChatRepo = MemberChatRepositoryMockImpl()
        memberRepo = MemberRepositoryMockImpl(memberChatRepo)
        chatRepo = ChatRepositoryMockImpl()
        groupRepo = GroupRepositoryMockImpl()
        groupMemberRepo = GroupMemberRepositoryMockImpl()

        // Wire services with real repos
        memberService = MemberService(memberRepo, memberChatRepo)
        chatService = ChatService(chatRepo)
        groupService = GroupService(groupRepo, groupMemberRepo)
        autoRegisterService = AutoRegisterService(memberService, chatService)

        // Mock Telegram API boundary
        bot = mockk(relaxed = true)
        botAdminUtils = mockk()
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
        every { botAdminUtils.isUserAdmin(any(), any(), any()) } returns false
        // Default: getChat returns a supergroup (tests can override per-case)
        every { bot.getChat(any()) } returns com.github.kotlintelegrambot.types.TelegramBotResult.Success(
            com.github.kotlintelegrambot.entities.Chat(id = testChatId, type = "supergroup")
        )

        // Real controllers
        groupController = GroupController(groupService, memberService, autoRegisterService)
        membersController = MembersController(memberService, autoRegisterService)
        registrationController = RegistrationController(autoRegisterService)
        pingController = PingController(memberService, groupService, autoRegisterService)

        // Real commands
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

    /**
     * Builds a Telegram Update with the given text and sender info.
     */
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

    /**
     * Pre-registers a member directly via MemberService (bypasses Telegram).
     */
    protected suspend fun registerMember(
        userId: Long = testUserId,
        username: String = testUsername,
        firstName: String = testFirstName,
        chatId: Long = testChatId,
        role: MemberRole = MemberRole.MEMBER
    ): MemberWithChat {
        return memberService.createMember(chatId, userId, username, firstName, role)
            .getOrThrow()
    }
}
