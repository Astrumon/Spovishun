package data.mapper

import com.ua.astrumon.data.db.table.Chats
import com.ua.astrumon.data.mapper.toChat
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMapperTest {
    @Test
    fun `toChat should map ResultRow to Chat`() {
        val now = Clock.System.now()
        val row = mockk<ResultRow>()
        every { row[Chats.chatId] } returns -1001234567890L
        every { row[Chats.title] } returns "Test Group"
        every { row[Chats.type] } returns "supergroup"
        every { row[Chats.registeredAt] } returns now
        every { row[Chats.announcementsEnabled] } returns true

        val chat = row.toChat()

        assertEquals(-1001234567890L, chat.chatId)
        assertEquals("Test Group", chat.title)
        assertEquals("supergroup", chat.type)
        assertEquals(now, chat.registeredAt)
        assertEquals(true, chat.announcementsEnabled)
    }

    @Test
    fun `toChat should handle nullable title and type`() {
        val now = Clock.System.now()
        val row = mockk<ResultRow>()
        every { row[Chats.chatId] } returns 100L
        every { row[Chats.title] } returns null
        every { row[Chats.type] } returns null
        every { row[Chats.registeredAt] } returns now
        every { row[Chats.announcementsEnabled] } returns false

        val chat = row.toChat()

        assertNull(chat.title)
        assertNull(chat.type)
    }
}
