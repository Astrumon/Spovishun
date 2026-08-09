package data.mapper

import com.ua.astrumon.data.bot.mapper.toGroup
import com.ua.astrumon.data.bot.table.GroupSettings
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.domain.bot.model.PingMark
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupMapperTest {
    @Test
    fun `toGroup should map ResultRow to Group`() {
        val row = groupRow(readinessEnabled = true, icon = "🔥")

        val group = row.toGroup()

        assertEquals(42L, group.id)
        assertEquals(100L, group.chatId)
        assertEquals("devs", group.name)
        assertTrue(group.memberUsernames.isEmpty())
        assertEquals(true, group.readinessEnabled)
        assertEquals("🔥", group.icon)
    }

    @Test
    fun `toGroup should carry a disabled readiness flag`() {
        assertEquals(false, groupRow(readinessEnabled = false, icon = null).toGroup().readinessEnabled)
    }

    @Test
    fun `toGroup should leave the icon null when the group has none`() {
        assertNull(groupRow(readinessEnabled = true, icon = null).toGroup().icon)
    }

    @Test
    fun `toGroup should read a stored ping mark as a custom one`() {
        val group = groupRow(readinessEnabled = true, icon = null, pingMark = "🦀").toGroup()

        assertEquals(PingMark.Custom("🦀"), group.pingMark)
    }

    @Test
    fun `toGroup should read an absent ping mark as the default`() {
        assertEquals(PingMark.Default, groupRow(readinessEnabled = true, icon = null).toGroup().pingMark)
    }

    /**
     * The flag outranks the column: a hidden mark keeps whatever emoji it was hiding, so a stored
     * value says nothing about whether it is rendered.
     */
    @Test
    fun `toGroup should read a disabled flag as hidden even with an emoji stored`() {
        val group = groupRow(readinessEnabled = true, icon = null, pingMark = "🦀", pingMarkEnabled = false).toGroup()

        assertEquals(PingMark.Hidden, group.pingMark)
    }

    /**
     * A group with no `group_settings` row at all — the LEFT JOIN yields nulls for every settings
     * column, and the mapper has to fall back to the table's own defaults.
     */
    @Test
    fun `toGroup should fall back to defaults when the settings row is absent`() {
        val row = mockk<ResultRow>()
        every { row[Groups.id] } returns EntityID(42L, Groups)
        every { row[Groups.chatId] } returns 100L
        every { row[Groups.name] } returns "devs"
        every { row.getOrNull(GroupSettings.readinessEnabled) } returns null
        every { row.getOrNull(GroupSettings.icon) } returns null
        every { row.getOrNull(GroupSettings.pingMark) } returns null
        every { row.getOrNull(GroupSettings.pingMarkEnabled) } returns null

        val group = row.toGroup()

        assertEquals(true, group.readinessEnabled)
        assertNull(group.icon)
        assertEquals(PingMark.Default, group.pingMark)
    }

    private fun groupRow(
        readinessEnabled: Boolean,
        icon: String?,
        pingMark: String? = null,
        pingMarkEnabled: Boolean = true,
    ): ResultRow {
        val row = mockk<ResultRow>()
        every { row[Groups.id] } returns EntityID(42L, Groups)
        every { row[Groups.chatId] } returns 100L
        every { row[Groups.name] } returns "devs"
        every { row.getOrNull(GroupSettings.readinessEnabled) } returns readinessEnabled
        every { row.getOrNull(GroupSettings.icon) } returns icon
        every { row.getOrNull(GroupSettings.pingMark) } returns pingMark
        every { row.getOrNull(GroupSettings.pingMarkEnabled) } returns pingMarkEnabled
        return row
    }
}
