package data.repository

import com.ua.astrumon.common.exception.BusinessException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.bot.repository.ChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupMemberRepositoryImpl
import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.data.bot.table.Members
import data.db.H2TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupMemberRepositoryImplTest {
    private val repository = GroupMemberRepositoryImpl()
    private val chatRepository = ChatRepositoryImpl()
    private val chatId = 100L

    @BeforeTest
    fun setup() {
        H2TestDatabaseFactory.initialize()
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

    private fun insertMember(
        username: String,
        userId: Long = username.hashCode().toLong(),
    ) {
        transaction {
            Members.insert {
                it[Members.userId] = userId
                it[Members.username] = username
                it[Members.firstname] = username.replaceFirstChar { c -> c.uppercase() }
            }
        }
    }

    private fun insertGroup(
        name: String,
        chatId: Long = this.chatId,
    ): Long = transaction {
        Groups
            .insert {
                it[Groups.chatId] = chatId
                it[Groups.name] = name
            }[Groups.id]
            .value
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
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `addMemberToGroup should return failure when member not exists`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")

        val result = repository.addMemberToGroup(chatId, "devs", "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `addMemberToGroup should return failure when member already in group`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertMember("alice")
        repository.addMemberToGroup(chatId, "devs", "alice")

        val result = repository.addMemberToGroup(chatId, "devs", "alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DuplicateResourceException)
    }

    @Test
    fun `getMembersForGroups should omit a group with no members`() = runTest {
        ensureChat(chatId)
        val devs = insertGroup("devs")

        val result = repository.getMembersForGroups(chatId, listOf(devs))

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow()[devs])
    }

    @Test
    fun `getMembersForGroups should return every group in one call`() = runTest {
        ensureChat(chatId)
        val devs = insertGroup("devs")
        val ops = insertGroup("ops")
        val empty = insertGroup("empty")
        insertMember("alice")
        insertMember("bob")
        repository.addMemberToGroup(chatId, "devs", "alice")
        repository.addMemberToGroup(chatId, "devs", "bob")
        repository.addMemberToGroup(chatId, "ops", "alice")

        val result = repository.getMembersForGroups(chatId, listOf(devs, ops, empty))

        assertTrue(result.isSuccess)
        val membersByGroup = result.getOrThrow()
        assertEquals(setOf("alice", "bob"), membersByGroup.getValue(devs).toSet())
        assertEquals(listOf("alice"), membersByGroup.getValue(ops))
        assertNull(membersByGroup[empty])
    }

    @Test
    fun `getMembersForGroups should keep insertion order within a group`() = runTest {
        ensureChat(chatId)
        val devs = insertGroup("devs")
        insertMember("zoe")
        insertMember("alice")
        repository.addMemberToGroup(chatId, "devs", "zoe")
        repository.addMemberToGroup(chatId, "devs", "alice")

        val result = repository.getMembersForGroups(chatId, listOf(devs))

        assertEquals(listOf("zoe", "alice"), result.getOrThrow().getValue(devs))
    }

    @Test
    fun `getMembersForGroups should return an empty map for an empty id list`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")

        val result = repository.getMembersForGroups(chatId, emptyList())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getMembersForGroups should ignore a group id from another chat`() = runTest {
        val otherChatId = 200L
        ensureChat(chatId)
        ensureChat(otherChatId)
        val foreign = insertGroup("devs", otherChatId)
        insertMember("alice")
        repository.addMemberToGroup(otherChatId, "devs", "alice")

        val result = repository.getMembersForGroups(chatId, listOf(foreign))

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getMembersForGroups should return an empty map for an unknown group id`() = runTest {
        ensureChat(chatId)

        val result = repository.getMembersForGroups(chatId, listOf(9999L))

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `removeMemberFromGroup should succeed when member is in group`() = runTest {
        ensureChat(chatId)
        val devs = insertGroup("devs")
        insertMember("alice")
        repository.addMemberToGroup(chatId, "devs", "alice")

        val result = repository.removeMemberFromGroup(chatId, "devs", "alice")

        assertTrue(result.isSuccess)

        val membersResult = repository.getMembersForGroups(chatId, listOf(devs))
        assertTrue(membersResult.getOrThrow().isEmpty())
    }

    @Test
    fun `removeMemberFromGroup should return failure when group not exists`() = runTest {
        insertMember("alice")

        val result = repository.removeMemberFromGroup(chatId, "nonexistent", "alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `removeMemberFromGroup should return failure when member not exists`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")

        val result = repository.removeMemberFromGroup(chatId, "devs", "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `removeMemberFromGroup should return failure when member not in group`() = runTest {
        ensureChat(chatId)
        insertGroup("devs")
        insertMember("alice")

        val result = repository.removeMemberFromGroup(chatId, "devs", "alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BusinessException)
    }

    @Test
    fun `members should be independent across groups`() = runTest {
        ensureChat(chatId)
        val devs = insertGroup("devs")
        val ops = insertGroup("ops")
        insertMember("alice")
        insertMember("bob")

        repository.addMemberToGroup(chatId, "devs", "alice")
        repository.addMemberToGroup(chatId, "devs", "bob")
        repository.addMemberToGroup(chatId, "ops", "alice")

        val membersByGroup = repository.getMembersForGroups(chatId, listOf(devs, ops)).getOrThrow()

        assertEquals(2, membersByGroup.getValue(devs).size)
        assertEquals(listOf("alice"), membersByGroup.getValue(ops))
    }

    @Test
    fun `removing member from one group should not affect other groups`() = runTest {
        ensureChat(chatId)
        val devs = insertGroup("devs")
        val ops = insertGroup("ops")
        insertMember("alice")

        repository.addMemberToGroup(chatId, "devs", "alice")
        repository.addMemberToGroup(chatId, "ops", "alice")

        repository.removeMemberFromGroup(chatId, "devs", "alice")

        val membersByGroup = repository.getMembersForGroups(chatId, listOf(devs, ops)).getOrThrow()

        assertNull(membersByGroup[devs])
        assertEquals(listOf("alice"), membersByGroup.getValue(ops))
    }
}
