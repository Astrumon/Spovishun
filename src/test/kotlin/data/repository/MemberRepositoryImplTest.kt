package data.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.data.db.repository.MemberRepositoryImpl
import com.ua.astrumon.data.db.table.Members
import data.db.TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberRepositoryImplTest {

    private val repository = MemberRepositoryImpl()

    @BeforeTest
    fun setup() {
        TestDatabaseFactory.initialize()
        transaction { Members.deleteAll() }
    }

    @Test
    fun `save should create member and return it`() = runTest {
        val result = repository.save(100L, 1L, "alice", "Alice", Clock.System.now())

        assertTrue(result.isSuccess)
        val member = result.getOrThrow()
        assertEquals(100L, member.chatId)
        assertEquals(1L, member.userId)
        assertEquals("alice", member.username)
        assertEquals("Alice", member.firstName)
        assertNotNull(member.joinedAt)
    }

    @Test
    fun `save should create member with null joinedAt`() = runTest {
        val result = repository.save(100L, 1L, "alice", "Alice", null)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().joinedAt)
    }

    @Test
    fun `save should return failure when username already exists`() = runTest {
        repository.save(100L, 1L, "alice", "Alice", null)

        val result = repository.save(100L, 2L, "alice", "Alice2", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is DuplicateResourceException)
    }

    @Test
    fun `findByUsername should return member when exists`() = runTest {
        repository.save(100L, 1L, "alice", "Alice", null)

        val result = repository.findByUsername("alice")

        assertTrue(result.isSuccess)
        val member = result.getOrThrow()
        assertNotNull(member)
        assertEquals("alice", member.username)
        assertEquals("Alice", member.firstName)
    }

    @Test
    fun `findByUsername should return null when not exists`() = runTest {
        val result = repository.findByUsername("nonexistent")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `findByUserId should return member when exists`() = runTest {
        repository.save(100L, 42L, "alice", "Alice", null)

        val result = repository.findByUserId(42L)

        assertTrue(result.isSuccess)
        val member = result.getOrThrow()
        assertNotNull(member)
        assertEquals(42L, member.userId)
        assertEquals("alice", member.username)
    }

    @Test
    fun `findByUserId should return null when not exists`() = runTest {
        val result = repository.findByUserId(999L)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `findAll should return empty list when no members`() = runTest {
        val result = repository.findAll()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `findAll should return all saved members`() = runTest {
        repository.save(100L, 1L, "alice", "Alice", null)
        repository.save(100L, 2L, "bob", "Bob", null)
        repository.save(100L, 3L, "charlie", "Charlie", null)

        val result = repository.findAll()

        assertTrue(result.isSuccess)
        val members = result.getOrThrow()
        assertEquals(3, members.size)
        assertEquals(setOf("alice", "bob", "charlie"), members.map { it.username }.toSet())
    }

    @Test
    fun `save should reject duplicate username even across chats`() = runTest {
        repository.save(100L, 1L, "alice", "Alice", null)

        val result = repository.save(200L, 2L, "alice", "Alice", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.cause is DuplicateResourceException)
    }
}
