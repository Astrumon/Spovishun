package presentation.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.util.ChatLogContext
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The registry is where the chat log context is actually wired into production (spovishun-168) —
 * a command is covered the moment it is registered. These tests hold that wiring, not just the
 * decorator it applies.
 */
class CommandRegistryTest {
    private val bot: Bot = mockk(relaxed = true)

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
        val message = Message(messageId = 1L, date = 0L, chat = chat, text = "/ping")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should preserve every command name`() {
        val registry = CommandRegistry(listOf(RecordingCommand("ping"), RecordingCommand("members")))

        assertEquals(listOf("ping", "members"), registry.commands.map { it.name })
    }

    @Test
    fun `should give every registered command the originating chat`() = runTest {
        val ping = RecordingCommand("ping")
        val members = RecordingCommand("members")
        val registry = CommandRegistry(listOf(ping, members))

        registry.commands.forEach { it.execute(bot, buildUpdate()) }

        assertEquals(chatId.toString(), ping.observedChatId)
        assertEquals(chatId.toString(), members.observedChatId)
    }

    @Test
    fun `should leave no chat context behind after a command runs`() = runTest {
        val registry = CommandRegistry(listOf(RecordingCommand("ping")))

        registry.commands.first().execute(bot, buildUpdate())

        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }
}
