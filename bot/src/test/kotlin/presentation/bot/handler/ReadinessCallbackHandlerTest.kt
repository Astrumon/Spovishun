package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.bot.handler.ReadinessCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.ReadinessVote
import com.ua.astrumon.presentation.bot.handler.SessionKey
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test

class ReadinessCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val runner: ReadinessSessionRunner = mockk()
    private lateinit var handler: ReadinessCallbackHandler

    private val chatId = 1L
    private val messageId = 5L
    private val clickerId = 2L
    private val key = SessionKey(chatId, messageId)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = ReadinessCallbackHandler(runner, testMessagesProvider())
    }

    private fun update(data: String) = callbackUpdate(chatId, clickerId, data, messageId)

    /** A re-render that has already landed — the handler should answer without waiting. */
    private fun completedRender(): Job = Job().apply { complete() }

    @Test
    fun `should record an accept vote`() = runTest {
        every { runner.onVote(bot, key, clickerId, ReadinessVote.ACCEPTED) } returns completedRender()

        handler.handle(bot, update("ready:a"))

        verify(exactly = 1) { runner.onVote(bot, key, clickerId, ReadinessVote.ACCEPTED) }
        verify(exactly = 1) { bot.answerCallbackQuery("cb", text = null) }
    }

    @Test
    fun `should record a decline vote`() = runTest {
        every { runner.onVote(bot, key, clickerId, ReadinessVote.DECLINED) } returns completedRender()

        handler.handle(bot, update("ready:d"))

        verify(exactly = 1) { runner.onVote(bot, key, clickerId, ReadinessVote.DECLINED) }
    }

    @Test
    fun `should tell an uninvited user they were not called`() = runTest {
        every { runner.onVote(bot, key, clickerId, ReadinessVote.ACCEPTED) } returns null
        every { runner.isLive(key) } returns true

        handler.handle(bot, update("ready:a"))

        verify(exactly = 1) { bot.answerCallbackQuery("cb", text = match { it.contains("Тебе не кликали") }) }
    }

    @Test
    fun `should tell a late voter the poll is over`() = runTest {
        every { runner.onVote(bot, key, clickerId, ReadinessVote.ACCEPTED) } returns null
        every { runner.isLive(key) } returns false

        handler.handle(bot, update("ready:a"))

        verify(exactly = 1) { bot.answerCallbackQuery("cb", text = match { it.contains("завершено") }) }
    }

    /**
     * The unanswered callback query is what keeps Telegram's spinner on the tapped button, so the
     * answer must not go out before the roster shows the vote.
     */
    @Test
    fun `should hold the callback answer until the re-render lands`() = runTest {
        val render = Job()
        every { runner.onVote(bot, key, clickerId, ReadinessVote.ACCEPTED) } returns render

        val handling = launch { handler.handle(bot, update("ready:a")) }
        runCurrent()
        verify(exactly = 0) { bot.answerCallbackQuery("cb", text = null) }

        render.complete()
        handling.join()

        verify(exactly = 1) { bot.answerCallbackQuery("cb", text = null) }
    }

    @Test
    fun `should stop spinning once the cap elapses even if the re-render stalls`() = runTest {
        every { runner.onVote(bot, key, clickerId, ReadinessVote.ACCEPTED) } returns Job()

        handler.handle(bot, update("ready:a"))

        verify(exactly = 1) { bot.answerCallbackQuery("cb", text = null) }
    }

    @Test
    fun `should ack and ignore an unknown payload`() = runTest {
        handler.handle(bot, update("ready:x"))

        verify(exactly = 0) { runner.onVote(any(), any(), any(), any()) }
        verify(exactly = 1) { bot.answerCallbackQuery("cb") }
    }
}
