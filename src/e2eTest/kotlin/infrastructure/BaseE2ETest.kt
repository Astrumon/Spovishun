package infrastructure

import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.data.memory.repository.ChatRepositoryMockImpl
import com.ua.astrumon.data.memory.repository.GroupMemberRepositoryMockImpl
import com.ua.astrumon.data.memory.repository.GroupRepositoryMockImpl
import com.ua.astrumon.data.memory.repository.MemberRepositoryMockImpl
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.ChatService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.MemberService
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
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
 * - Verification is done via in-memory repository state (not getUpdates, which
 *   is blocked for bot-to-bot communication by Telegram).
 * - Tests confirm: real API connectivity, auth, admin detection, command logic.
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

    // In-memory repos (fresh per test, shared with command handlers)
    protected lateinit var memberRepo: MemberRepositoryMockImpl
    protected lateinit var chatRepo: ChatRepositoryMockImpl
    protected lateinit var groupRepo: GroupRepositoryMockImpl
    protected lateinit var groupMemberRepo: GroupMemberRepositoryMockImpl

    // Real services
    protected lateinit var memberService: MemberService
    protected lateinit var chatService: ChatService
    protected lateinit var groupService: GroupService
    protected lateinit var autoRegisterService: AutoRegisterService

    // Command handlers (for direct dispatch)
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var messageHandler: MessageHandler

    protected val testChatId: Long get() = E2EConfig.testChatId!!

    @BeforeTest
    fun setUpE2E() {
        assumeTrue(E2EConfig.isConfigured, "E2E env vars not set — skipping e2e tests")

        runBlocking {
            helperBot = TelegramHelperBot(E2EConfig.helperBotToken!!, testChatId)
            helperBotId = helperBot.getBotId(E2EConfig.helperBotToken!!)
        }

        memberRepo = MemberRepositoryMockImpl()
        chatRepo = ChatRepositoryMockImpl()
        groupRepo = GroupRepositoryMockImpl()
        groupMemberRepo = GroupMemberRepositoryMockImpl()

        memberService = MemberService(memberRepo)
        chatService = ChatService(chatRepo)
        groupService = GroupService(groupRepo, groupMemberRepo)
        autoRegisterService = AutoRegisterService(memberService, chatService)

        val botAdminUtils = BotAdminUtils()

        val groupController = GroupController(groupService, memberService, autoRegisterService)
        val membersController = MembersController(memberService, autoRegisterService)
        val registrationController = RegistrationController(memberService, autoRegisterService)
        val pingController = PingController(memberService, groupService, autoRegisterService)

        messageHandler = MessageHandler(autoRegisterService, botAdminUtils)
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
            )
        )

        val telegramBot = TelegramBot(commandRegistry, messageHandler)
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
        if (E2EConfig.isConfigured) helperBot.close()
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
    }

    /**
     * Build a synthetic Update as if [helperBotId] sent [text] in the test group.
     */
    protected fun buildUpdate(
        text: String,
        userId: Long = helperBotId,
        username: String = "helper_bot",
        firstName: String = "HelperBot",
        chatId: Long = testChatId,
        chatType: String = "supergroup"
    ): Update {
        val user = User(id = userId, isBot = true, firstName = firstName, username = username)
        val chat = Chat(id = chatId, type = chatType)
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = text)
        return Update(updateId = 1L, message = message)
    }

    /**
     * Dispatch [command] directly into the main bot's handler.
     * The bot makes real Telegram API calls (sendMessage, getChatMember, etc.).
     * Returns without error if the command was processed successfully.
     */
    protected fun dispatch(command: String) {
        runBlocking { dispatchCommand(command, buildUpdate(text = command)) }
    }

    protected suspend fun dispatchCommand(command: String, update: Update) {
        val name = command.trimStart('/').split(" ").firstOrNull() ?: return
        val cmd = commandRegistry.commands.find { it.name == name }
        if (cmd != null) cmd.execute(mainBot, update)
        else messageHandler.handleIncomingMessage(mainBot, update)
    }

    /** Pre-register a member directly via service (bypasses Telegram). */
    protected fun registerMember(
        userId: Long,
        username: String,
        firstName: String,
        role: MemberRole = MemberRole.MEMBER
    ): Member = runBlocking {
        memberService.createMember(testChatId, userId, username, firstName, role).getOrThrow()
    }

    /** Convenience: get all members from the in-memory repo. */
    protected fun allMembers() = runBlocking { memberService.getAllMembers().getOrThrow() }

    /** Convenience: get all groups from the in-memory repo. */
    protected fun allGroups() = runBlocking { groupService.getAllGroupsWithMembers(testChatId).getOrThrow() }
}
