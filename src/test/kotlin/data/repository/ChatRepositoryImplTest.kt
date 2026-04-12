package data.repository

import com.ua.astrumon.data.db.repository.ChatRepositoryImpl
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatRepositoryImplTest {

    private val repository = ChatRepositoryImpl()

    @BeforeTest
    fun setup() {
        TestDatabaseFactory.initialize()
        transaction {
            GroupMembers.deleteAll()
            Groups.deleteAll()
            Chats.deleteAll()
        }
    }

    @Test
    fun `save should create and return chat`() = runTest {
        val result = repository.save(100L, "Test Group", "group")

        assertTrue(result.isSuccess)
        val chat = result.getOrThrow()
        assertEquals(100L, chat.chatId)
        assertEquals("Test Group", chat.title)
        assertEquals("group", chat.type)
        assertNotNull(chat.registeredAt)
    }

    @Test
    fun `save should return existing chat when already exists`() = runTest {
        repository.save(100L, "Test Group", "group")

        val result = repository.save(100L, "Different Title", "supergroup")

        assertTrue(result.isSuccess)
        val chat = result.getOrThrow()
        assertEquals(100L, chat.chatId)
        assertEquals("Test Group", chat.title)
        assertEquals("group", chat.type)
    }

    @Test
    fun `findById should return chat when exists`() = runTest {
        repository.save(100L, "Test Group", "group")

        val result = repository.findById(100L)

        assertTrue(result.isSuccess)
        val chat = result.getOrThrow()
        assertNotNull(chat)
        assertEquals(100L, chat.chatId)
        assertEquals("Test Group", chat.title)
    }

    @Test
    fun `findById should return null when not exists`() = runTest {
        val result = repository.findById(999L)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `save should handle null title and type`() = runTest {
        val result = repository.save(100L, null, null)

        assertTrue(result.isSuccess)
        val chat = result.getOrThrow()
        assertEquals(100L, chat.chatId)
        assertNull(chat.title)
        assertNull(chat.type)
    }
}
