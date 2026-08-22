package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.domain.bot.config.ReadinessConfig
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionStore
import com.ua.astrumon.presentation.bot.handler.ReadinessVote
import com.ua.astrumon.presentation.bot.handler.SessionKey
import com.ua.astrumon.presentation.util.ChatLogContext
import com.ua.astrumon.presentation.util.withChatLogContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.slf4j.MDC
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class ReadinessSessionRunnerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val store = ReadinessSessionStore()
    private val dispatcher = StandardTestDispatcher()

    // Mirrors the production binding in di/ConfigModule: a SupervisorJob, so one failing poll cannot
    // cancel the polls running beside it. A plain TestScope would let a single throw kill them all
    // and quietly hide the failure behind unrelated assertions.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val config = object : ReadinessConfig {
        override val readinessTtl: Duration = TTL
    }
    private lateinit var runner: ReadinessSessionRunner

    private val chatId = 1L
    private val messageId = 5L
    private val key = SessionKey(chatId, messageId)
    private val alice = Member(1L, 100L, "alice", "Alice")
    private val bob = Member(2L, 200L, "bob", "Bob")
    private val members = listOf(alice, bob)

    @BeforeTest
    fun setup() {
        runner = ReadinessSessionRunner(store, scope, config)
        every {
            bot.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns TelegramBotResult.Success(sentMessage())
        // `editMessageText` returns `Pair<Response<Message?>?, Exception?>`, and a relaxed mock fills
        // both erased slots with a bare `Object`. `editInPlace` reads `.second` as the declared
        // `Exception?`, so the relaxed default fails the cast. It never surfaced before spovishun-190
        // because the `runCatching` that used to wrap the call caught the ClassCastException too.
        every {
            bot.editMessageText(any(), any(), any(), any(), any(), any(), any())
        } returns Pair(null, null)
    }

    private fun sentMessage(): Message = Message(
        messageId = messageId,
        date = 0L,
        chat = Chat(id = chatId, type = "group"),
    )

    /**
     * Opens the poll on behalf of someone outside the roster by default, so every case that predates
     * the initiator auto-vote (spovishun-183) still starts from an empty tally.
     */
    private fun startPoll(initiatorId: Long = OUTSIDER_ID) =
        runner.start(bot, chatId, ReadinessSession(ukMessages, "header", members), initiatorId)

    @Test
    fun `should publish the poll with a keyboard and register the session`() = runTest(dispatcher) {
        startPoll()

        assertTrue(runner.isLive(key))
        verify(exactly = 1) {
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = match { it.contains("header") && it.contains("@alice") },
                parseMode = ParseMode.HTML,
                replyMarkup = any<InlineKeyboardMarkup>(),
            )
        }
    }

    /**
     * The initiator's 👍 must be part of the opening message, not an edit that follows it — an edit
     * would spend a rate-limit slot to say what the first render could have said itself.
     */
    @Test
    fun `should count an initiator who is a member as ready in the first render`() = runTest(dispatcher) {
        startPoll(initiatorId = alice.userId)

        assertEquals(ReadinessVote.ACCEPTED, store.get(key)?.votes?.get(alice.userId))
        verify(exactly = 1) {
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = match { it.contains("👍 @alice") && it.contains("⏳ @bob") },
                parseMode = ParseMode.HTML,
                replyMarkup = any<InlineKeyboardMarkup>(),
            )
        }
        verify(exactly = 0) { bot.editMessageText(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should not vote for an initiator who is not a member`() = runTest(dispatcher) {
        startPoll(initiatorId = OUTSIDER_ID)

        assertEquals(emptyMap(), store.get(key)?.votes)
        // Asserted on the rendered roster too, not only on the tally it is derived from: the promise
        // this case carries is about what the chat sees.
        verify(exactly = 1) {
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = match { it.contains("⏳ @alice") && it.contains("⏳ @bob") },
                parseMode = ParseMode.HTML,
                replyMarkup = any<InlineKeyboardMarkup>(),
            )
        }
    }

    @Test
    fun `should let the initiator overrule their automatic vote`() = runTest(dispatcher) {
        startPoll(initiatorId = alice.userId)

        runner.onVote(bot, key, alice.userId, ReadinessVote.DECLINED)

        assertEquals(ReadinessVote.DECLINED, store.get(key)?.votes?.get(alice.userId))
    }

    @Test
    fun `should not register a session when Telegram returns no message`() = runTest(dispatcher) {
        every {
            bot.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns TelegramBotResult.Error.Unknown(IllegalStateException("boom"))

        startPoll()

        assertFalse(runner.isLive(key))
    }

    /**
     * The poll's deferred work runs on an injected scope, which carries no chat of its own — so
     * without the snapshot handed to `launch` it would log as `system` (spovishun-168). Both
     * coroutines are asserted: the coalesced re-render and the TTL expiry.
     */
    @Test
    fun `should carry the opening chat context into the deferred render and expiry`() = runTest(dispatcher) {
        var chatIdDuringRender: String? = null
        var chatIdDuringExpiry: String? = null
        every { bot.editMessageText(any(), any(), any(), any(), any(), any(), any()) } answers {
            val seen = MDC.get(ChatLogContext.CHAT_ID)
            if (chatIdDuringRender == null) chatIdDuringRender = seen else chatIdDuringExpiry = seen
            Pair(null, null)
        }

        withChatLogContext(chatId, "supergroup") {
            startPoll()
            runner.onVote(bot, key, alice.userId, ReadinessVote.ACCEPTED)
        }
        advanceUntilIdle()

        assertEquals(chatId.toString(), chatIdDuringRender)
        assertEquals(chatId.toString(), chatIdDuringExpiry)
        assertNull(MDC.get(ChatLogContext.CHAT_ID))
    }

    @Test
    fun `should coalesce a burst of votes into a single edit`() = runTest(dispatcher) {
        startPoll()

        runner.onVote(bot, key, alice.userId, ReadinessVote.ACCEPTED)
        runner.onVote(bot, key, bob.userId, ReadinessVote.DECLINED)
        advanceTimeBy(COALESCE_WINDOW + ONE_TICK)

        verify(exactly = 1) { bot.editMessageText(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should reject a vote from someone who was not invited`() = runTest(dispatcher) {
        startPoll()

        val render = runner.onVote(bot, key, OUTSIDER_ID, ReadinessVote.ACCEPTED)
        advanceTimeBy(COALESCE_WINDOW + ONE_TICK)

        assertNull(render)
        verify(exactly = 0) { bot.editMessageText(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should freeze the poll without a keyboard once the TTL expires`() = runTest(dispatcher) {
        startPoll()
        runner.onVote(bot, key, alice.userId, ReadinessVote.ACCEPTED)

        advanceUntilIdle()

        assertFalse(runner.isLive(key))
        assertNull(store.get(key))
        verify(exactly = 1) {
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                text = match { it.contains("Підсумок") },
                parseMode = ParseMode.HTML,
                replyMarkup = null,
            )
        }
    }

    /**
     * The frozen summary must be the last thing written. A coalesced render that is still pending
     * when the TTL fires must never repaint the live roster over it.
     */
    @Test
    fun `should write the summary after any pending re-render`() = runTest(dispatcher) {
        startPoll()
        runner.onVote(bot, key, alice.userId, ReadinessVote.ACCEPTED)

        advanceUntilIdle()

        verifyOrder {
            bot.editMessageText(any(), any(), any(), match { it.contains("👍 @alice") }, any(), any(), any())
            bot.editMessageText(any(), any(), any(), match { it.contains("Підсумок") }, any(), any(), null)
        }
    }

    @Test
    fun `should ignore a vote arriving after the poll was finalized`() = runTest(dispatcher) {
        startPoll()
        advanceUntilIdle()

        val render = runner.onVote(bot, key, alice.userId, ReadinessVote.ACCEPTED)

        assertNull(render)
    }

    @Test
    fun `should keep the last vote of a member who changes their mind`() = runTest(dispatcher) {
        startPoll()

        runner.onVote(bot, key, alice.userId, ReadinessVote.ACCEPTED)
        runner.onVote(bot, key, alice.userId, ReadinessVote.DECLINED)

        assertEquals(ReadinessVote.DECLINED, store.get(key)?.votes?.get(alice.userId))
    }

    private companion object {
        val TTL = 30.minutes
        val COALESCE_WINDOW = 1_500.milliseconds
        val ONE_TICK = 1.milliseconds
        const val OUTSIDER_ID = 999L
    }
}
