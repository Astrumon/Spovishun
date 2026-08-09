package commands

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.util.ChatLogContext
import infrastructure.BaseIntegrationTest
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * The end-to-end proof of spovishun-168: a command dispatched through the real [CommandRegistry]
 * over a real PostgreSQL, with every line it emits captured and inspected.
 *
 * The unit tests hold each seam in isolation; this one holds the property the task actually asks
 * for — that *nothing* in the chain drops the chat, including the `:data` layer, which only logs
 * after `safeDbQuery` has moved the coroutine onto `Dispatchers.IO`.
 */
class ChatLogContextIntegrationTest : BaseIntegrationTest() {
    private val context = LoggerFactory.getILoggerFactory() as LoggerContext
    private val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as LogbackLogger
    private val capture = ListAppender<ILoggingEvent>()
    private lateinit var originalLevel: Level

    @BeforeTest
    fun attachCapture() {
        MDC.clear()
        capture.context = context
        // The lines under test are appended from Dispatchers.IO threads while this one reads them.
        // ListAppender's default ArrayList is not safe for that; a copy-on-write list is.
        capture.list = CopyOnWriteArrayList()
        capture.start()
        root.addAppender(capture)
        // The data layer traces at DEBUG; without this the very lines that prove the dispatcher hop
        // never reach the appender. Exposed and Hikari stay pinned to WARN by logback.xml.
        originalLevel = root.level
        root.level = Level.DEBUG
    }

    @AfterTest
    fun detachCapture() {
        root.level = originalLevel
        root.detachAppender(capture)
        capture.stop()
        MDC.clear()
    }

    /** Only our own loggers are in scope — third-party noise is not this task's contract. */
    private fun spovishunEvents(): List<ILoggingEvent> = capture.list.filter { it.loggerName.startsWith("com.ua.astrumon") }

    private suspend fun dispatchMembersCommand() {
        val registry = CommandRegistry(listOf(membersCommand), autoRegistrar)
        capture.list.clear()
        registry.commands.single().execute(bot, buildUpdate("/members"))
    }

    @Test
    fun `every line logged while handling a command names the originating chat`() = runTest {
        dispatchMembersCommand()

        val events = spovishunEvents()
        assertTrue(events.isNotEmpty(), "command dispatch produced no log lines to assert on")
        val withoutChat = events.filter { it.mdcPropertyMap[ChatLogContext.CHAT_ID] != testChatId.toString() }
        assertTrue(
            withoutChat.isEmpty(),
            "lines missing chat context: ${withoutChat.map { "${it.loggerName}: ${it.message}" }}",
        )
    }

    @Test
    fun `chat context survives the safeDbQuery dispatcher hop into the data layer`() = runTest {
        dispatchMembersCommand()

        val dataLayerEvents = capture.list.filter { it.loggerName == DATABASE_FACTORY_LOGGER }
        assertTrue(dataLayerEvents.isNotEmpty(), "no :data layer lines captured — the query never ran")
        dataLayerEvents.forEach {
            assertEquals(testChatId.toString(), it.mdcPropertyMap[ChatLogContext.CHAT_ID], it.message)
        }
    }

    @Test
    fun `chat type is carried alongside the chat id`() = runTest {
        dispatchMembersCommand()

        assertTrue(spovishunEvents().all { it.mdcPropertyMap[ChatLogContext.CHAT_TYPE] == "supergroup" })
    }

    @Test
    fun `no chat context leaks past the end of the dispatch`() = runTest {
        dispatchMembersCommand()

        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    @Test
    fun `a second chat does not inherit the first chat's context`() = runTest {
        val otherChatId = -1009999999999L
        val registry = CommandRegistry(listOf(membersCommand), autoRegistrar)
        registry.commands.single().execute(bot, buildUpdate("/members"))
        capture.list.clear()

        registry.commands.single().execute(bot, buildUpdate("/members", chatId = otherChatId))

        assertTrue(spovishunEvents().all { it.mdcPropertyMap[ChatLogContext.CHAT_ID] == otherChatId.toString() })

        cleaner.cleanupByChatId(otherChatId)
    }

    private companion object {
        const val DATABASE_FACTORY_LOGGER = "com.ua.astrumon.data.db.DatabaseFactory"
    }
}
