package data.mapper

import com.ua.astrumon.data.db.table.MemberChats
import com.ua.astrumon.data.mapper.toMemberChat
import com.ua.astrumon.domain.model.MemberRole
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberChatMapperTest {

    @Test
    fun `toMemberChat should map ResultRow to MemberChat`() {
        val now = Clock.System.now()
        val row = mockk<ResultRow>()
        every { row[MemberChats.memberId] } returns 1L
        every { row[MemberChats.chatId] } returns 100L
        every { row[MemberChats.role] } returns "MODERATOR"
        every { row[MemberChats.joinedAt] } returns now

        val memberChat = row.toMemberChat()

        assertEquals(1L, memberChat.memberId)
        assertEquals(100L, memberChat.chatId)
        assertEquals(MemberRole.MODERATOR, memberChat.role)
        assertEquals(now, memberChat.joinedAt)
    }

    @Test
    fun `toMemberChat should handle null joinedAt`() {
        val row = mockk<ResultRow>()
        every { row[MemberChats.memberId] } returns 1L
        every { row[MemberChats.chatId] } returns 100L
        every { row[MemberChats.role] } returns "MEMBER"
        every { row[MemberChats.joinedAt] } returns null

        val memberChat = row.toMemberChat()

        assertNull(memberChat.joinedAt)
    }
}
