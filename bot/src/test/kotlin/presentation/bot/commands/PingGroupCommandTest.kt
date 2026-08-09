package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.PingGroupCommand
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PickerOption
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test

class PingGroupCommandTest {
    private val pingController: PingController = mockk()
    private val readinessSessionRunner: ReadinessSessionRunner = mockk(relaxed = true)
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: PingGroupCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = PingGroupCommand(pingController, readinessSessionRunner, testMessagesProvider())
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()
    }

    private fun createUpdate(
        fromUser: User? = user,
        text: String = "/ping",
    ): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should delegate to controller and send message`() = runTest {
        val update = createUpdate(text = "/ping devs")
        coEvery { pingController.pingGroup(chatId, listOf("devs")) } returns
            PingOutcome.Plain(CommandResponse.Success("📣 🦞\n\n@alice @bob"))

        command.execute(bot, update)

        coVerify { pingController.pingGroup(chatId, listOf("devs")) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "📣 🦞\n\n@alice @bob", ParseMode.HTML) }
    }

    @Test
    fun `should send not found message with available groups`() = runTest {
        val update = createUpdate(text = "/ping unknown")
        coEvery { pingController.pingGroup(chatId, listOf("unknown")) } returns
            PingOutcome.Plain(CommandResponse.NotFound("Група", "unknown", listOf("devs", "qa")))

        command.execute(bot, update)

        coVerify {
            bot.sendMessage(
                ChatId.fromId(chatId),
                match { it.contains("не знайдено") && it.contains("devs") && it.contains("qa") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `should show inline keyboard when no args and groups exist`() = runTest {
        val update = createUpdate(text = "/ping")
        coEvery { pingController.groupsForPicker(chatId) } returns
            PickerListing.Show(
                listOf(
                    PickerOption(PingController.ALL_MEMBERS_ID, "📢 Усі учасники"),
                    PickerOption(1L, "devs"),
                    PickerOption(2L, "qa"),
                ),
            )

        command.execute(bot, update)

        coVerify { pingController.groupsForPicker(chatId) }
        coVerify { bot.sendMessage(chatId = ChatId.fromId(chatId), text = any(), parseMode = ParseMode.HTML, replyMarkup = any()) }
    }

    @Test
    fun `should still show the keyboard when no args and the chat has no groups`() = runTest {
        val update = createUpdate(text = "/ping")
        coEvery { pingController.groupsForPicker(chatId) } returns
            PickerListing.Show(listOf(PickerOption(PingController.ALL_MEMBERS_ID, "📢 Усі учасники")))

        command.execute(bot, update)

        coVerify { bot.sendMessage(chatId = ChatId.fromId(chatId), text = any(), parseMode = ParseMode.HTML, replyMarkup = any()) }
    }

    @Test
    fun `should send error message when no args and groupsForPicker rejects`() = runTest {
        val update = createUpdate(text = "/ping")
        coEvery { pingController.groupsForPicker(chatId) } returns
            PickerListing.Reject(CommandResponse.Error("Failed to load groups"))

        command.execute(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), "❌ Failed to load groups", ParseMode.HTML) }
    }

    @Test
    fun `should return early when message is null`() = runTest {
        val update = Update(updateId = 1L, message = null)

        command.execute(bot, update)

        coVerify(exactly = 0) { pingController.pingGroup(any(), any()) }
    }

    @Test
    fun `should start a readiness poll instead of a plain message when readiness is on`() = runTest {
        val update = createUpdate(text = "/ping devs")
        val members = listOf(Member(1L, userId, "alice", "Alice"))
        coEvery { pingController.pingGroup(chatId, listOf("devs")) } returns
            PingOutcome.Readiness("📣 devs 🦞", members)

        command.execute(bot, update)

        verify(exactly = 1) { readinessSessionRunner.start(bot, chatId, ReadinessSession(ukMessages, "📣 devs 🦞", members)) }
        verify(exactly = 0) { bot.sendMessage(ChatId.fromId(chatId), any<String>(), ParseMode.HTML) }
    }

    @Test
    fun `should toggle group readiness on with the ready-on flag`() = runTest {
        val update = createUpdate(text = "/ping devs \$ready-on")
        coEvery { pingController.setGroupReadiness(chatId, userId, "devs", true) } returns CommandResponse.Success("on")

        command.execute(bot, update)

        coVerify(exactly = 1) { pingController.setGroupReadiness(chatId, userId, "devs", true) }
        coVerify(exactly = 0) { pingController.pingGroup(any(), any()) }
    }

    @Test
    fun `should toggle group readiness off with the ready-off flag`() = runTest {
        val update = createUpdate(text = "/ping devs \$READY-OFF")
        coEvery { pingController.setGroupReadiness(chatId, userId, "devs", false) } returns CommandResponse.Success("off")

        command.execute(bot, update)

        coVerify(exactly = 1) { pingController.setGroupReadiness(chatId, userId, "devs", false) }
    }

    @Test
    fun `should answer usage instead of silently dropping text after the toggle`() = runTest {
        val update = createUpdate(text = "/ping devs \$ready-off бо шумно")

        command.execute(bot, update)

        coVerify(exactly = 0) { pingController.setGroupReadiness(any(), any(), any(), any()) }
        coVerify(exactly = 0) { pingController.pingGroup(any(), any()) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("Використання") }, ParseMode.HTML) }
    }

    @Test
    fun `should treat a non-flag second argument as extra ping text`() = runTest {
        val update = createUpdate(text = "/ping devs ready")
        coEvery { pingController.pingGroup(chatId, listOf("devs", "ready")) } returns
            PingOutcome.Plain(CommandResponse.Success("ok"))

        command.execute(bot, update)

        coVerify(exactly = 0) { pingController.setGroupReadiness(any(), any(), any(), any()) }
        coVerify(exactly = 1) { pingController.pingGroup(chatId, listOf("devs", "ready")) }
    }
}
