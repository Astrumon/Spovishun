package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.bot.commands.ChatContextCommand
import com.ua.astrumon.presentation.util.ChatLogContext
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChatContextCommandTest {
    private val bot: Bot = mockk(relaxed = true)

    private val chatId = -1009876543210L
    private val chatType = "supergroup"

    /** Records what the chat context looked like at the moment the real command ran. */
    private class RecordingCommand(
        override val name: String = "ping",
        private val onExecute: suspend () -> Unit = {},
    ) : BotCommand {
        var observedChatId: String? = null
        var observedChatType: String? = null

        override suspend fun execute(
            bot: Bot,
            update: Update,
        ) {
            observedChatId = MDC.get(ChatLogContext.CHAT_ID)
            observedChatType = MDC.get(ChatLogContext.CHAT_TYPE)
            onExecute()
        }
    }

    @BeforeTest
    fun setup() = MDC.clear()

    @AfterTest
    fun tearDown() = MDC.clear()

    private fun buildUpdate(chatIdValue: Long = chatId): Update {
        val chat = Chat(id = chatIdValue, type = chatType)
        val message = Message(messageId = 1L, date = 0L, chat = chat, text = "/ping")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should delegate the command name`() {
        assertEquals("members", ChatContextCommand(RecordingCommand(name = "members")).name)
    }

    @Test
    fun `should expose the originating chat while the command runs`() = runTest {
        val delegate = RecordingCommand()

        ChatContextCommand(delegate).execute(bot, buildUpdate())

        assertEquals(chatId.toString(), delegate.observedChatId)
        assertEquals(chatType, delegate.observedChatType)
    }

    @Test
    fun `should clear the chat context once the command completes`() = runTest {
        ChatContextCommand(RecordingCommand()).execute(bot, buildUpdate())

        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    @Test
    fun `should clear the chat context when the command throws`() = runTest {
        val delegate = RecordingCommand(onExecute = { error("boom") })

        assertFailsWith<IllegalStateException> {
            ChatContextCommand(delegate).execute(bot, buildUpdate())
        }

        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    @Test
    fun `should keep the chat context when the command hops dispatchers`() = runTest {
        var chatIdOnIo: String? = null
        val delegate = RecordingCommand(
            onExecute = { withContext(Dispatchers.IO) { chatIdOnIo = MDC.get(ChatLogContext.CHAT_ID) } },
        )

        ChatContextCommand(delegate).execute(bot, buildUpdate())

        assertEquals(chatId.toString(), chatIdOnIo)
    }

    @Test
    fun `should run the command with no chat context when the update carries no message`() = runTest {
        val delegate = RecordingCommand()

        ChatContextCommand(delegate).execute(bot, Update(updateId = 1L, message = null))

        assertNull(delegate.observedChatId)
        assertNull(delegate.observedChatType)
    }
}
