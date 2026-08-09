package data.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.bot.repository.MemberRepositoryImpl
import com.ua.astrumon.data.bot.table.Members
import com.ua.astrumon.domain.bot.model.BirthDate
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
    fun `updateBirthday should persist the date and return the stored member`() = runTest {
        repository.saveOrUpdate(1L, "alice", "Alice")

        val result = repository.updateBirthday(1L, BirthDate(day = 7, month = 3))

        assertTrue(result.isSuccess)
        assertEquals(BirthDate(day = 7, month = 3), result.getOrThrow().birthday)
        assertEquals(BirthDate(day = 7, month = 3), repository.findByUserId(1L).getOrThrow()?.birthday)
    }

    @Test
    fun `updateBirthday with null should clear the date`() = runTest {
        repository.saveOrUpdate(1L, "alice", "Alice")
        repository.updateBirthday(1L, BirthDate(day = 7, month = 3))

        val result = repository.updateBirthday(1L, null)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().birthday)
    }

    @Test
    fun `updateBirthday should fail for an unknown userId`() = runTest {
        val result = repository.updateBirthday(999L, BirthDate(day = 7, month = 3))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
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

    @Test
    fun `findAllByUsernames should return every match in one call`() = runTest {
        repository.saveOrUpdate(1L, "alice", "Alice")
        repository.saveOrUpdate(2L, "bob", "Bob")
        repository.saveOrUpdate(3L, "carol", "Carol")

        val result = repository.findAllByUsernames(listOf("alice", "carol"))

        assertTrue(result.isSuccess)
        assertEquals(setOf("alice", "carol"), result.getOrThrow().map { it.username }.toSet())
    }

    @Test
    fun `findAllByUsernames should match case-insensitively like findByUsername`() = runTest {
        repository.saveOrUpdate(1L, "Alice", "Alice")

        val result = repository.findAllByUsernames(listOf("aLiCe"))

        assertEquals(listOf("Alice"), result.getOrThrow().map { it.username })
    }

    @Test
    fun `findAllByUsernames should silently drop usernames with no member row`() = runTest {
        repository.saveOrUpdate(1L, "alice", "Alice")

        val result = repository.findAllByUsernames(listOf("alice", "ghost"))

        assertEquals(listOf("alice"), result.getOrThrow().map { it.username })
    }

    @Test
    fun `findAllByUsernames should return empty without querying for an empty input`() = runTest {
        repository.saveOrUpdate(1L, "alice", "Alice")

        val result = repository.findAllByUsernames(emptyList())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}
