package presentation.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.util.ChatLogContext
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The registry is where the chat log context (spovishun-168) and the caller registration
 * (spovishun-172) are actually wired into production — a command is covered by both the moment it
 * is registered. These tests hold that wiring, not just the decorators it applies.
 */
class CommandRegistryTest {
    private val bot: Bot = mockk(relaxed = true)
    private val autoRegistrar: MemberAutoRegistrar = mockk(relaxed = true)

    private val chatId = -1005555555555L

    private class RecordingCommand(
        override val name: String,
    ) : BotCommand {
        var observedChatId: String? = null

        override suspend fun execute(
            bot: Bot,
            update: Update,
        ) {
            observedChatId = MDC.get(ChatLogContext.CHAT_ID)
        }
    }

    @BeforeTest
    fun setup() = MDC.clear()

    @AfterTest
    fun tearDown() = MDC.clear()

    private fun buildUpdate(): Update {
        val chat = Chat(id = chatId, type = "group")
        val user = User(id = 7L, isBot = false, firstName = "Alice", username = "alice")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = "/ping")
        return Update(updateId = 1L, message = message)
    }

    private fun registry(vararg commands: BotCommand) = CommandRegistry(commands.toList(), autoRegistrar)

    @Test
    fun `should preserve every command name`() {
        val registry = registry(RecordingCommand("ping"), RecordingCommand("members"))

        assertEquals(listOf("ping", "members"), registry.commands.map { it.name })
    }

    @Test
    fun `should give every registered command the originating chat`() = runTest {
        val ping = RecordingCommand("ping")
        val members = RecordingCommand("members")

        registry(ping, members).commands.forEach { it.execute(bot, buildUpdate()) }

        assertEquals(chatId.toString(), ping.observedChatId)
        assertEquals(chatId.toString(), members.observedChatId)
    }

    /** Registering a command is the whole opt-in — there is no per-command auto-register call left. */
    @Test
    fun `should register the caller for every registered command`() = runTest {
        registry(RecordingCommand("ping"), RecordingCommand("members")).commands.forEach { it.execute(bot, buildUpdate()) }

        coVerify(exactly = 2) { autoRegistrar.ensure(bot, match { it.id == chatId }, match { it.id == 7L }) }
    }

    @Test
    fun `should leave no chat context behind after a command runs`() = runTest {
        registry(RecordingCommand("ping")).commands.first().execute(bot, buildUpdate())

        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }
}
