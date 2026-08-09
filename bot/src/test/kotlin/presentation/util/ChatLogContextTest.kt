package presentation.util

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
}
