package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.presentation.bot.commands.AutoRegisterCommand
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoRegisterCommandTest {
    private val bot: Bot = mockk(relaxed = true)
    private val autoRegistrar: MemberAutoRegistrar = mockk(relaxed = true)

    private val chatId = -100L
    private val userId = 42L

    /** Records whether the wrapped command ran, and whether registration had already happened. */
    private class RecordingCommand(
        override val name: String = "ping",
    ) : BotCommand {
        var executed = false

        override suspend fun execute(
            bot: Bot,
            update: Update,
        ) {
            executed = true
        }
    }

    @BeforeTest
    fun setup() = clearAllMocks()

    private fun buildUpdate(from: User? = User(id = userId, isBot = false, firstName = "Alice", username = "alice")): Update {
        val chat = Chat(id = chatId, type = "supergroup")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = from, text = "/ping")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should delegate the command name`() {
        assertEquals("members", AutoRegisterCommand(RecordingCommand(name = "members"), autoRegistrar).name)
    }

    @Test
    fun `should register the caller before running the command`() = runTest {
        val delegate = RecordingCommand()
        var registeredBeforeExecute = false
        coEvery { autoRegistrar.ensure(any(), any(), any()) } answers { registeredBeforeExecute = !delegate.executed }

        AutoRegisterCommand(delegate, autoRegistrar).execute(bot, buildUpdate())

        coVerify(exactly = 1) { autoRegistrar.ensure(bot, match { it.id == chatId }, match { it.id == userId }) }
        assertTrue(registeredBeforeExecute)
        assertTrue(delegate.executed)
    }

    @Test
    fun `should still run the command when the update carries no sender`() = runTest {
        val delegate = RecordingCommand()

        AutoRegisterCommand(delegate, autoRegistrar).execute(bot, buildUpdate(from = null))

        coVerify(exactly = 0) { autoRegistrar.ensure(any(), any(), any()) }
        assertTrue(delegate.executed)
    }

    @Test
    fun `should still run the command when the update carries no message`() = runTest {
        val delegate = RecordingCommand()

        AutoRegisterCommand(delegate, autoRegistrar).execute(bot, Update(updateId = 1L, message = null))

        coVerify(exactly = 0) { autoRegistrar.ensure(any(), any(), any()) }
        assertTrue(delegate.executed)
    }
}
