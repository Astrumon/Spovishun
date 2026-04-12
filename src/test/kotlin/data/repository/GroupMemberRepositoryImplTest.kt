package data.repository

import com.ua.astrumon.common.exception.BusinessException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.db.repository.ChatRepositoryImpl
import com.ua.astrumon.data.db.repository.GroupMemberRepositoryImpl
import com.ua.astrumon.data.db.table.Chats
import com.ua.astrumon.data.db.table.GroupMembers
import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.data.db.table.Members
import data.db.TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupMemberRepositoryImplTest {

    private val repository = GroupMemberRepositoryImpl()
    private val chatRepository = ChatRepositoryImpl()
    private val chatId = 100L

    @BeforeTest
    fun setup() {
        TestDatabaseFactory.initialize()
        transaction {
            GroupMembers.deleteAll()
            Groups.deleteAll()
            Members.deleteAll()
            Chats.deleteAll()
        }
    }

    private suspend fun ensureChat(chatId: Long) {
        chatRepository.save(chatId, null, null)
    }

    private fun insertMember(username: String, userId: Long = username.hashCode().toLong()) {
        val cid = chatId
        transaction {
            Members.insert {
                it[Members.chatId] = cid
                it[Members.userId] = userId
                it[Members.username] = username
                it[Members.firstname] = username.replaceFirstChar { c -> c.uppercase() }
                it[Members.joinedAt] = Clock.System.now()
            }
        }
    }

    private fun insertGroup(name: String) {
        val cid = chatId
        transaction {
            Groups.insert {
                it[Groups.chatId] = cid
                it[Groups.name] = name
            }
        }
    }

    @Test
    fun `addMemberToGroup should succeed when group and member exist`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertMember("alice")

        val result = repository.addMemberToGroup(chatId, "devs", "alice")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `addMemberToGroup should return failure when group not exists`() = runTest {
        insertMember("alice")

        val result = repository.addMemberToGroup(chatId, "nonexistent", "alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
    }

    @Test
    fun `addMemberToGroup should return failure when member not exists`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")

        val result = repository.addMemberToGroup(chatId, "devs", "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
    }

    @Test
    fun `addMemberToGroup should return failure when member already in group`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertMember("alice")
        repository.addMemberToGroup(chatId, "devs", "alice")

        val result = repository.addMemberToGroup(chatId, "devs", "alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is DuplicateResourceException)
    }

    @Test
    fun `getGroupMembers should return empty list for group with no members`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")

        val result = repository.getGroupMembers(chatId, "devs")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getGroupMembers should return all members of group`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertMember("alice")
        insertMember("bob")
        repository.addMemberToGroup(chatId, "devs", "alice")
        repository.addMemberToGroup(chatId, "devs", "bob")

        val result = repository.getGroupMembers(chatId, "devs")

        assertTrue(result.isSuccess)
        val members = result.getOrThrow()
        assertEquals(2, members.size)
        assertEquals(setOf("alice", "bob"), members.toSet())
    }

    @Test
    fun `getGroupMembers should return failure when group not exists`() = runTest {
        val result = repository.getGroupMembers(chatId, "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
    }

    @Test
    fun `removeMemberFromGroup should succeed when member is in group`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertMember("alice")
        repository.addMemberToGroup(chatId, "devs", "alice")

        val result = repository.removeMemberFromGroup(chatId, "devs", "alice")

        assertTrue(result.isSuccess)

        val membersResult = repository.getGroupMembers(chatId, "devs")
        assertTrue(membersResult.getOrThrow().isEmpty())
    }

    @Test
    fun `removeMemberFromGroup should return failure when group not exists`() = runTest {
        insertMember("alice")

        val result = repository.removeMemberFromGroup(chatId, "nonexistent", "alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
    }

    @Test
    fun `removeMemberFromGroup should return failure when member not exists`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")

        val result = repository.removeMemberFromGroup(chatId, "devs", "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is ResourceNotFoundException)
    }

    @Test
    fun `removeMemberFromGroup should return failure when member not in group`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertMember("alice")

        val result = repository.removeMemberFromGroup(chatId, "devs", "alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is BusinessException)
    }

    @Test
    fun `members should be independent across groups`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertGroup("ops")
        insertMember("alice")
        insertMember("bob")

        repository.addMemberToGroup(chatId, "devs", "alice")
        repository.addMemberToGroup(chatId, "devs", "bob")
        repository.addMemberToGroup(chatId, "ops", "alice")

        val devsResult = repository.getGroupMembers(chatId, "devs")
        assertEquals(2, devsResult.getOrThrow().size)

        val opsResult = repository.getGroupMembers(chatId, "ops")
        assertEquals(1, opsResult.getOrThrow().size)
        assertEquals("alice", opsResult.getOrThrow().first())
    }

    @Test
    fun `removing member from one group should not affect other groups`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertGroup("ops")
        insertMember("alice")

        repository.addMemberToGroup(chatId, "devs", "alice")
        repository.addMemberToGroup(chatId, "ops", "alice")

        repository.removeMemberFromGroup(chatId, "devs", "alice")

        val devsResult = repository.getGroupMembers(chatId, "devs")
        assertTrue(devsResult.getOrThrow().isEmpty())

        val opsResult = repository.getGroupMembers(chatId, "ops")
        assertEquals(listOf("alice"), opsResult.getOrThrow())
    }
}
