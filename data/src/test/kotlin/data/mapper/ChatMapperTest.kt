package data.mapper

import com.ua.astrumon.data.bot.mapper.toChat
import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.domain.bot.model.BotLanguage
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
        every { row[Chats.readinessEnabled] } returns true
        every { row[Chats.language] } returns "en"

        val chat = row.toChat()

        assertEquals(BotLanguage.EN, chat.language)
        assertEquals(-1001234567890L, chat.chatId)
        assertEquals("Test Group", chat.title)
        assertEquals("supergroup", chat.type)
        assertEquals(now, chat.registeredAt)
        assertEquals(true, chat.announcementsEnabled)
        assertEquals(true, chat.readinessEnabled)
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
        every { row[Chats.readinessEnabled] } returns false
        // A code the enum does not know must not blow up the mapper — it degrades to Ukrainian.
        every { row[Chats.language] } returns "de"

        val chat = row.toChat()

        assertNull(chat.title)
        assertNull(chat.type)
        assertEquals(false, chat.readinessEnabled)
        assertEquals(BotLanguage.UK, chat.language)
    }
}
