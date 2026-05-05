package data.mapper

import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.data.mapper.toGroup
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupMapperTest {

    @Test
    fun `toGroup should map ResultRow to Group`() {
        val row = mockk<ResultRow>()
        every { row[Groups.id] } returns EntityID(42L, Groups)
        every { row[Groups.chatId] } returns 100L
        every { row[Groups.name] } returns "devs"

        val group = row.toGroup()

        assertEquals(42L, group.id)
        assertEquals(100L, group.chatId)
        assertEquals("devs", group.name)
        assertTrue(group.memberUsernames.isEmpty())
    }
}
