package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.PingCommand
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PingCommandTest {

    private val pingController: PingController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var pingCommand: PingCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        pingCommand = PingCommand(pingController, botAdminUtils)
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun createUpdate(fromUser: User? = user, text: String = "/all"): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    // --- pingAll ---

    @Test
    fun `pingAll should delegate to controller and send message`() = runTest {
        val update = createUpdate()
        coEvery { pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()) } returns
            CommandResponse.Success("📢 🗿\n\n@alice @bob")

        pingCommand.pingAll(bot, update)

        coVerify { pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "📢 🗿\n\n@alice @bob", ParseMode.HTML) }
    }

    @Test
    fun `pingAll should include extra args`() = runTest {
        val update = createUpdate(text = "/all standup time")
        coEvery { pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("standup", "time")) } returns
            CommandResponse.Success("📢 🗿 standup time\n\n@alice")

        pingCommand.pingAll(bot, update)

        coVerify { pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("standup", "time")) }
    }

    @Test
    fun `pingAll should send error message on controller failure`() = runTest {
        val update = createUpdate()
        coEvery { pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()) } returns
            CommandResponse.Error("Failed to load members")

        pingCommand.pingAll(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("❌") }, ParseMode.HTML) }
    }

    @Test
    fun `pingAll should return early when message is null`() = runTest {
        val update = Update(updateId = 1L, message = null)

        pingCommand.pingAll(bot, update)

        coVerify(exactly = 0) { pingController.pingAll(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `pingAll should use user_id as username when username is null`() = runTest {
        val noUsernameUser = User(id = userId, isBot = false, firstName = "Alice", username = null)
        val update = createUpdate(fromUser = noUsernameUser)
        coEvery { pingController.pingAll(chatId, userId, "user_$userId", "Alice", MemberRole.MEMBER, emptyList()) } returns
            CommandResponse.Success("ok")

        pingCommand.pingAll(bot, update)

        coVerify { pingController.pingAll(chatId, userId, "user_$userId", "Alice", MemberRole.MEMBER, emptyList()) }
    }

    // --- pingGroup ---

    @Test
    fun `pingGroup should delegate to controller and send message`() = runTest {
        val update = createUpdate(text = "/ping devs")
        coEvery { pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs")) } returns
            CommandResponse.Success("📣 🦞\n\n@alice @bob")

        pingCommand.pingGroup(bot, update)

        coVerify { pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs")) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "📣 🦞\n\n@alice @bob", ParseMode.HTML) }
    }

    @Test
    fun `pingGroup should send not found message with available groups`() = runTest {
        val update = createUpdate(text = "/ping unknown")
        coEvery { pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("unknown")) } returns
            CommandResponse.NotFound("Група", "unknown", listOf("devs", "qa"))

        pingCommand.pingGroup(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("не знайдено") && it.contains("devs") && it.contains("qa") }, ParseMode.HTML) }
    }

    @Test
    fun `pingGroup should send usage message when no args`() = runTest {
        val update = createUpdate(text = "/ping")
        coEvery { pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()) } returns
            CommandResponse.Error("Використання: /ping &lt;група&gt; [текст]")

        pingCommand.pingGroup(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("/ping") }, ParseMode.HTML) }
    }

    @Test
    fun `pingGroup should return early when message is null`() = runTest {
        val update = Update(updateId = 1L, message = null)

        pingCommand.pingGroup(bot, update)

        coVerify(exactly = 0) { pingController.pingGroup(any(), any(), any(), any(), any(), any()) }
    }
}
