package data.mapper

import com.ua.astrumon.data.bot.mapper.toMember
import com.ua.astrumon.data.bot.mapper.toMemberWithChat
import com.ua.astrumon.data.bot.table.MemberChats
import com.ua.astrumon.data.bot.table.Members
import com.ua.astrumon.domain.bot.model.MemberRole
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberMapperTest {
    @Test
    fun `toMember should map ResultRow to Member`() {
        val row = mockk<ResultRow>()
        every { row[Members.id] } returns EntityID(1L, Members)
        every { row[Members.userId] } returns 12345L
        every { row[Members.username] } returns "john"
        every { row[Members.firstname] } returns "John"
        every { row[Members.birthMd] } returns null

        val member = row.toMember()

        assertEquals(1L, member.id)
        assertEquals(12345L, member.userId)
        assertEquals("john", member.username)
        assertEquals("John", member.firstName)
    }

    @Test
    fun `toMemberWithChat should map ResultRow to MemberWithChat`() {
        val now = Clock.System.now()
        val row = mockk<ResultRow>()
        every { row[Members.id] } returns EntityID(1L, Members)
        every { row[Members.userId] } returns 12345L
        every { row[Members.username] } returns "john"
        every { row[Members.firstname] } returns "John"
        every { row[Members.birthMd] } returns null
        every { row[MemberChats.role] } returns "ADMIN"
        every { row[MemberChats.joinedAt] } returns now

        val memberWithChat = row.toMemberWithChat()

        assertEquals(1L, memberWithChat.id)
        assertEquals(12345L, memberWithChat.userId)
        assertEquals("john", memberWithChat.username)
        assertEquals("John", memberWithChat.firstName)
        assertEquals(MemberRole.ADMIN, memberWithChat.role)
        assertEquals(now, memberWithChat.joinedAt)
    }

    @Test
    fun `toMemberWithChat should handle null joinedAt`() {
        val row = mockk<ResultRow>()
        every { row[Members.id] } returns EntityID(1L, Members)
        every { row[Members.userId] } returns 12345L
        every { row[Members.username] } returns "john"
        every { row[Members.firstname] } returns "John"
        every { row[Members.birthMd] } returns null
        every { row[MemberChats.role] } returns "MEMBER"
        every { row[MemberChats.joinedAt] } returns null

        val memberWithChat = row.toMemberWithChat()

        assertNull(memberWithChat.joinedAt)
    }
}
