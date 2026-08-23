package presentation.util

import com.github.kotlintelegrambot.entities.Chat
import com.ua.astrumon.presentation.util.ChatLogContext
import com.ua.astrumon.presentation.util.withChatLogContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChatLogContextTest {
    private val chatId = -1001234567890L
    private val chatType = "supergroup"

    @BeforeTest
    fun setup() = MDC.clear()

    @AfterTest
    fun tearDown() = MDC.clear()

    @Test
    fun `should expose chat id and type inside the block`() = runTest {
        withChatLogContext(chatId, chatType) {
            assertEquals(chatId.toString(), MDC.get(ChatLogContext.CHAT_ID))
            assertEquals(chatType, MDC.get(ChatLogContext.CHAT_TYPE))
        }
    }

    @Test
    fun `should clear the chat context once the block completes`() = runTest {
        withChatLogContext(chatId, chatType) { }

        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    @Test
    fun `should clear the chat context when the block throws`() = runTest {
        assertFailsWith<IllegalStateException> {
            withChatLogContext(chatId, chatType) { error("boom") }
        }

        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    /**
     * The reason this helper exists: `safeDbQuery` funnels every repository call through
     * `withContext(Dispatchers.IO)`, and a bare `MDC.put` — being a thread-local — would not survive
     * the hop. Anything logged from the data layer must still name the chat.
     */
    @Test
    fun `should keep the chat context across a dispatcher hop`() = runTest {
        withChatLogContext(chatId, chatType) {
            withContext(Dispatchers.IO) {
                assertEquals(chatId.toString(), MDC.get(ChatLogContext.CHAT_ID))
                assertEquals(chatType, MDC.get(ChatLogContext.CHAT_TYPE))
            }
        }
    }

    @Test
    fun `should restore the outer chat when a nested block completes`() = runTest {
        val innerChatId = 42L

        withChatLogContext(chatId, chatType) {
            withChatLogContext(innerChatId, "private") {
                assertEquals(innerChatId.toString(), MDC.get(ChatLogContext.CHAT_ID))
                assertEquals("private", MDC.get(ChatLogContext.CHAT_TYPE))
            }

            assertEquals(chatId.toString(), MDC.get(ChatLogContext.CHAT_ID))
            assertEquals(chatType, MDC.get(ChatLogContext.CHAT_TYPE))
        }
    }

    @Test
    fun `should expose the chat title when one is given`() = runTest {
        withChatLogContext(chatId, chatType, "Astrumon Team") {
            assertEquals("Astrumon Team", MDC.get(ChatLogContext.CHAT_TITLE))
        }
    }

    @Test
    fun `should take the group title as the chat name`() = runTest {
        withChatLogContext(chat(title = "Astrumon Team", username = "astrumon", firstName = "Danylo")) {
            assertEquals("Astrumon Team", MDC.get(ChatLogContext.CHAT_TITLE))
        }
    }

    /**
     * `security.md` allows only anonymized identifiers in a log line. A private chat is one person,
     * so `username` and `firstName` name them outright — neither may become the chat's log name,
     * however convenient a fallback it would be. `chatId` already correlates those lines.
     */
    @Test
    fun `should never name a private chat by its username or first name`() = runTest {
        withChatLogContext(chat(title = null, username = "astrumon", firstName = "Danylo")) {
            assertNull(MDC.get(ChatLogContext.CHAT_TITLE))
            assertEquals(chatId.toString(), MDC.get(ChatLogContext.CHAT_ID))
        }
    }

    /**
     * A bracket in the title would close the `[chat=…]` field early and a newline would split the
     * line in two — either lets a chat corrupt the log format by renaming itself.
     */
    @Test
    fun `should strip brackets and line breaks from the title`() = runTest {
        withChatLogContext(chat(title = "Team [DEV]\nsquad")) {
            assertEquals("Team  DEV  squad", MDC.get(ChatLogContext.CHAT_TITLE))
        }
    }

    @Test
    fun `should truncate a title longer than the log field allows`() = runTest {
        withChatLogContext(chat(title = "A".repeat(200))) {
            assertEquals("A".repeat(64), MDC.get(ChatLogContext.CHAT_TITLE))
        }
    }

    /**
     * `take` counts UTF-16 code units, so a cut landing between the halves of an emoji would put a
     * lone surrogate into the log line. 63 characters plus one emoji straddles the limit exactly.
     */
    @Test
    fun `should not leave half an emoji at the truncation boundary`() = runTest {
        withChatLogContext(chat(title = "A".repeat(63) + "😀")) {
            val title = MDC.get(ChatLogContext.CHAT_TITLE)

            assertEquals("A".repeat(63), title)
            assertFalse(title.last().isHighSurrogate())
        }
    }

    @Test
    fun `should drop a title the sanitizer empties`() = runTest {
        withChatLogContext(chat(title = "[]")) {
            assertNull(MDC.get(ChatLogContext.CHAT_TITLE))
        }
    }

    @Test
    fun `should preserve unrelated context entries`() = runTest {
        MDC.put("requestId", "abc")

        withChatLogContext(chatId, chatType) {
            assertEquals("abc", MDC.get("requestId"))
        }
    }

    @Test
    fun `should leave the key absent when chat type is unknown`() = runTest {
        withChatLogContext(chatId) {
            assertEquals(chatId.toString(), MDC.get(ChatLogContext.CHAT_ID))
            assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
        }
    }

    @Test
    fun `should leave both keys absent when there is no chat`() = runTest {
        withChatLogContext(chatId = null, chatType = null) {
            assertNull(MDC.get(ChatLogContext.CHAT_ID))
            assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
        }
    }

    @Test
    fun `should drop the outer chat when a nested block has none`() = runTest {
        withChatLogContext(chatId, chatType) {
            withChatLogContext(chatId = null, chatType = null) {
                assertNull(MDC.get(ChatLogContext.CHAT_ID))
                assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
            }

            assertEquals(chatId.toString(), MDC.get(ChatLogContext.CHAT_ID))
        }
    }

    private fun chat(
        title: String? = null,
        username: String? = null,
        firstName: String? = null,
    ) = Chat(id = chatId, type = chatType, title = title, username = username, firstName = firstName)
}
