package data.repository

import com.ua.astrumon.data.bot.repository.MemberRepositoryImpl
import com.ua.astrumon.data.bot.table.Members
import data.db.H2TestDatabaseFactory
import kotlinx.coroutines.test.runTest
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
        H2TestDatabaseFactory.initialize()
        transaction { Members.deleteAll() }
    }

    @Test
    fun `saveOrUpdate should create member and return it`() = runTest {
        val result = repository.saveOrUpdate(1L, "alice", "Alice")

        assertTrue(result.isSuccess)
        val member = result.getOrThrow()
        assertEquals(1L, member.userId)
        assertEquals("alice", member.username)
        assertEquals("Alice", member.firstName)
    }

    @Test
    fun `saveOrUpdate should update username and firstName when called again with same userId`() = runTest {
        repository.saveOrUpdate(1L, "alice", "Alice")

        val result = repository.saveOrUpdate(1L, "alice_new", "Alice New")

        assertTrue(result.isSuccess)
        val member = result.getOrThrow()
        assertEquals("alice_new", member.username)
        assertEquals("Alice New", member.firstName)
    }

    @Test
    fun `findByUsername should return member when exists`() = runTest {
        repository.saveOrUpdate(1L, "alice", "Alice")

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
        repository.saveOrUpdate(42L, "alice", "Alice")

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
    fun `findById should return member when exists`() = runTest {
        val saved = repository.saveOrUpdate(1L, "alice", "Alice").getOrThrow()

        val result = repository.findById(saved.id)

        assertTrue(result.isSuccess)
        val member = result.getOrThrow()
        assertNotNull(member)
        assertEquals(saved.id, member.id)
        assertEquals("alice", member.username)
    }

    @Test
    fun `findById should return null when not exists`() = runTest {
        val result = repository.findById(99999L)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
}
