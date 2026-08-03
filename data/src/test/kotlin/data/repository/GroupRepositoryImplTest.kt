package data.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.bot.repository.ChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupRepositoryImpl
import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.GroupSettings
import com.ua.astrumon.data.bot.table.Groups
import data.db.H2TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupRepositoryImplTest {
    private val repository = GroupRepositoryImpl()
    private val chatRepository = ChatRepositoryImpl()

    @BeforeTest
    fun setup() {
        H2TestDatabaseFactory.initialize()
        transaction {
            GroupMembers.deleteAll()
            GroupSettings.deleteAll()
            Groups.deleteAll()
            Chats.deleteAll()
        }
    }

    private suspend fun ensureChat(chatId: Long) {
        chatRepository.save(chatId, null, null)
    }

    @Test
    fun `createGroup should create and return group`() = runTest {
        ensureChat(100L)
        val result = repository.createGroup(100L, "devs")

        assertTrue(result.isSuccess)
        val group = result.getOrThrow()
        assertEquals(100L, group.chatId)
        assertEquals("devs", group.name)
        assertTrue(group.memberUsernames.isEmpty())
    }

    @Test
    fun `createGroup should return failure when duplicate name in same chat`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val result = repository.createGroup(100L, "devs")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DuplicateResourceException)
    }

    @Test
    fun `createGroup should allow same name in different chats`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        val result1 = repository.createGroup(100L, "devs")
        val result2 = repository.createGroup(200L, "devs")

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun `findGroupByKey should return group when exists`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val result = repository.findGroupByKey(100L, "devs")

        assertTrue(result.isSuccess)
        val group = result.getOrThrow()
        assertEquals("devs", group.name)
        assertEquals(100L, group.chatId)
    }

    @Test
    fun `findGroupByKey should return failure when not exists`() = runTest {
        val result = repository.findGroupByKey(100L, "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `findGroupByKey should not find group from different chat`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val result = repository.findGroupByKey(200L, "devs")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `getAllGroups should return empty list when no groups`() = runTest {
        val result = repository.getAllGroups(100L)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAllGroups should return only groups for given chatId`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        repository.createGroup(100L, "devs")
        repository.createGroup(100L, "ops")
        repository.createGroup(200L, "other")

        val result = repository.getAllGroups(100L)

        assertTrue(result.isSuccess)
        val groups = result.getOrThrow()
        assertEquals(2, groups.size)
        assertEquals(setOf("devs", "ops"), groups.map { it.name }.toSet())
    }

    @Test
    fun `deleteGroup should remove existing group`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val deleteResult = repository.deleteGroup(100L, "devs")
        assertTrue(deleteResult.isSuccess)

        val findResult = repository.findGroupByKey(100L, "devs")
        assertTrue(findResult.isFailure)
    }

    @Test
    fun `deleteGroup should return failure when group not exists`() = runTest {
        val result = repository.deleteGroup(100L, "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `deleteGroup should not delete group from different chat`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val deleteResult = repository.deleteGroup(200L, "devs")
        assertTrue(deleteResult.isFailure)

        val findResult = repository.findGroupByKey(100L, "devs")
        assertTrue(findResult.isSuccess)
    }

    @Test
    fun `a new group should have readiness enabled`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        assertTrue(repository.findGroupByKey(100L, "devs").getOrThrow().readinessEnabled)
    }

    @Test
    fun `setReadinessEnabled should persist the flag`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.setReadinessEnabled(100L, "devs", false)

        assertTrue(update.isSuccess)
        assertEquals(false, repository.findGroupByKey(100L, "devs").getOrThrow().readinessEnabled)
    }

    @Test
    fun `setReadinessEnabled should fail for an unknown group`() = runTest {
        ensureChat(100L)

        val result = repository.setReadinessEnabled(100L, "nonexistent", false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `setReadinessEnabled should not touch a same-named group in another chat`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        repository.createGroup(100L, "devs")
        repository.createGroup(200L, "devs")

        repository.setReadinessEnabled(100L, "devs", false)

        assertTrue(repository.findGroupByKey(200L, "devs").getOrThrow().readinessEnabled)
    }

    @Test
    fun `a new group should have no icon`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        assertNull(repository.findGroupByKey(100L, "devs").getOrThrow().icon)
    }

    @Test
    fun `setIcon should persist the icon`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.setIcon(100L, "devs", "🔥")

        assertTrue(update.isSuccess)
        assertEquals("🔥", repository.findGroupByKey(100L, "devs").getOrThrow().icon)
    }

    @Test
    fun `setIcon should surface the icon through getAllGroups`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.setIcon(100L, "devs", "🔥")

        assertEquals(
            "🔥",
            repository
                .getAllGroups(100L)
                .getOrThrow()
                .single()
                .icon,
        )
    }

    @Test
    fun `setIcon with null should clear the icon`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.setIcon(100L, "devs", "🔥")

        val update = repository.setIcon(100L, "devs", null)

        assertTrue(update.isSuccess)
        assertNull(repository.findGroupByKey(100L, "devs").getOrThrow().icon)
    }

    @Test
    fun `setIcon should fail for an unknown group`() = runTest {
        ensureChat(100L)

        val result = repository.setIcon(100L, "nonexistent", "🔥")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `setIcon should not touch a same-named group in another chat`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        repository.createGroup(100L, "devs")
        repository.createGroup(200L, "devs")

        repository.setIcon(100L, "devs", "🔥")

        assertNull(repository.findGroupByKey(200L, "devs").getOrThrow().icon)
    }

    /** Both settings share one row, so each write must name only the column it owns. */
    @Test
    fun `setIcon and setReadinessEnabled should not clobber each other`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        repository.setReadinessEnabled(100L, "devs", false)
        repository.setIcon(100L, "devs", "🔥")

        val group = repository.findGroupByKey(100L, "devs").getOrThrow()
        assertEquals("🔥", group.icon)
        assertEquals(false, group.readinessEnabled)

        repository.setReadinessEnabled(100L, "devs", true)

        val updated = repository.findGroupByKey(100L, "devs").getOrThrow()
        assertEquals("🔥", updated.icon)
        assertTrue(updated.readinessEnabled)
    }

    @Test
    fun `deleting a group should remove its settings row`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.setIcon(100L, "devs", "🔥")

        repository.deleteGroup(100L, "devs")

        assertEquals(0, transaction { GroupSettings.selectAll().count() })
    }
}
