package logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.read.ListAppender
import com.ua.astrumon.presentation.util.ChatLogContext
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * Holds the wire contract the Spovishun Admin live-log view parses (spovishun-168).
 *
 * The encoder under test is the one the real `app/src/main/resources/logback.xml` configures — read
 * off the running [LoggerContext], not a copy of the pattern string — so editing the pattern without
 * updating the client breaks here rather than in production.
 */
class LogbackChatContextPatternTest {
    private val context = LoggerFactory.getILoggerFactory() as LoggerContext
    private val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as LogbackLogger
    private val capture = ListAppender<ILoggingEvent>()

    private val chatId = -1001234567890L

    @BeforeTest
    fun setup() {
        MDC.clear()
        capture.context = context
        capture.start()
        root.addAppender(capture)
    }

    @AfterTest
    fun tearDown() {
        root.detachAppender(capture)
        capture.stop()
        MDC.clear()
    }

    /**
     * Renders a real logged event through the configured console encoder. Selects by logger name
     * rather than taking the only element — the suite shares a JVM, so a stray line from elsewhere
     * must not decide whether this test passes.
     */
    private fun renderLogLine(logAction: () -> Unit): String {
        capture.list.clear()
        logAction()
        val appender = root.getAppender("STDOUT") as OutputStreamAppender<ILoggingEvent>
        return String(appender.encoder.encode(capture.list.first { it.loggerName == TEST_LOGGER }))
    }

    @Test
    fun `should render the chat fields in a fixed parseable position`() {
        val line = renderLogLine {
            MDC.put(ChatLogContext.CHAT_ID, chatId.toString())
            MDC.put(ChatLogContext.CHAT_TYPE, "supergroup")
            LoggerFactory.getLogger(TEST_LOGGER).info("Command 'ping' invoked")
        }

        assertTrue(line.contains("[chatId=$chatId chatType=supergroup]"), line)
    }

    @Test
    fun `should render the chat title in its own trailing group`() {
        val line = renderLogLine {
            MDC.put(ChatLogContext.CHAT_ID, chatId.toString())
            MDC.put(ChatLogContext.CHAT_TYPE, "supergroup")
            MDC.put(ChatLogContext.CHAT_TITLE, "Astrumon Team")
            LoggerFactory.getLogger(TEST_LOGGER).info("Command 'ping' invoked")
        }

        assertTrue(line.contains("[chatId=$chatId chatType=supergroup] [chat=Astrumon Team]"), line)
    }

    @Test
    fun `should render a line with no originating chat as system`() {
        val line = renderLogLine {
            LoggerFactory.getLogger(TEST_LOGGER).info("Initializing database")
        }

        assertTrue(line.contains("[chatId=system chatType=system] [chat=system]"), line)
    }

    /** The exact expression the Admin client uses to split the field back out of a raw Docker line. */
    @Test
    fun `should be parseable by the documented client regex`() {
        val line = renderLogLine {
            MDC.put(ChatLogContext.CHAT_ID, chatId.toString())
            MDC.put(ChatLogContext.CHAT_TYPE, "private")
            LoggerFactory.getLogger(TEST_LOGGER).info("hello")
        }

        val match = Regex("""\[chatId=(\S+) chatType=(\S+)]""").find(line)

        assertEquals(listOf(chatId.toString(), "private"), match?.destructured?.toList())
    }

    /**
     * The whole reason the title got its own bracket group (spovishun-194): a client that predates
     * the field must keep parsing chatId/chatType out of a line that now carries a spaced title.
     * If this fails, the server can no longer ship ahead of the Admin client.
     */
    @Test
    fun `should stay parseable by the pre-title client regex once a title is present`() {
        val line = renderLogLine {
            MDC.put(ChatLogContext.CHAT_ID, chatId.toString())
            MDC.put(ChatLogContext.CHAT_TYPE, "supergroup")
            MDC.put(ChatLogContext.CHAT_TITLE, "Astrumon Team")
            LoggerFactory.getLogger(TEST_LOGGER).info("hello")
        }

        val match = Regex("""\[chatId=(\S+) chatType=(\S+)]""").find(line)

        assertEquals(listOf(chatId.toString(), "supergroup"), match?.destructured?.toList())
    }

    /** The expression the Admin client adds to read the new field. */
    @Test
    fun `should have its title read by the documented title regex`() {
        val line = renderLogLine {
            MDC.put(ChatLogContext.CHAT_TITLE, "Astrumon Team")
            LoggerFactory.getLogger(TEST_LOGGER).info("hello")
        }

        val match = Regex("""\[chat=([^]]*)]""").find(line)

        assertEquals("Astrumon Team", match?.groupValues?.get(1))
    }

    private companion object {
        const val TEST_LOGGER = "com.ua.astrumon.Example"
    }
}
