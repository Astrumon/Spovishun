package data.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.db.repository.ChatRepositoryImpl
import com.ua.astrumon.data.db.repository.GroupRepositoryImpl
import com.ua.astrumon.data.db.table.Chats
import com.ua.astrumon.data.db.table.GroupMembers
import com.ua.astrumon.data.db.table.Groups
import data.db.TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupRepositoryImplTest {

    private val repository = GroupRepositoryImpl()
    private val chatRepository = ChatRepositoryImpl()

    @BeforeTest
    fun setup() {
        TestDatabaseFactory.initialize()
        transaction {
            GroupMembers.deleteAll()
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
        assertTrue(result.exceptionOrNull()?.cause is DuplicateResourceException)
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
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
    }

    @Test
    fun `findGroupByKey should not find group from different chat`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val result = repository.findGroupByKey(200L, "devs")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
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
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
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
}
