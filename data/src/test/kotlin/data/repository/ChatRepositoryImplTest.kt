package data.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.bot.repository.ChatRepositoryImpl
import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.domain.bot.model.BotLanguage
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

class ChatRepositoryImplTest {
    private val repository = ChatRepositoryImpl()

    @BeforeTest
    fun setup() {
        H2TestDatabaseFactory.initialize()
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

    @Test
    fun `a new chat should have readiness enabled`() = runTest {
        val result = repository.save(100L, null, null)

        assertTrue(result.getOrThrow().readinessEnabled)
    }

    @Test
    fun `setReadinessEnabled should persist the flag`() = runTest {
        repository.save(100L, null, null)

        val update = repository.setReadinessEnabled(100L, false)

        assertTrue(update.isSuccess)
        assertEquals(false, repository.findById(100L).getOrThrow()?.readinessEnabled)
    }

    @Test
    fun `setReadinessEnabled should fail for an unknown chat`() = runTest {
        val result = repository.setReadinessEnabled(999L, false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `setReadinessEnabled should not touch the announcements flag`() = runTest {
        repository.save(100L, null, null)
        repository.setAnnouncementsEnabled(100L, false)

        repository.setReadinessEnabled(100L, false)

        assertEquals(false, repository.findById(100L).getOrThrow()?.announcementsEnabled)
    }

    @Test
    fun `a new chat should default to Ukrainian`() = runTest {
        val result = repository.save(100L, null, null)

        assertEquals(BotLanguage.UK, result.getOrThrow().language)
    }

    @Test
    fun `setLanguage should persist the choice`() = runTest {
        repository.save(100L, null, null)

        val update = repository.setLanguage(100L, BotLanguage.EN)

        assertTrue(update.isSuccess)
        assertEquals(BotLanguage.EN, repository.findById(100L).getOrThrow()?.language)
    }

    @Test
    fun `setLanguage should fail for an unknown chat`() = runTest {
        val result = repository.setLanguage(999L, BotLanguage.EN)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `findAnnouncementChats should return only opted-in chats with their language`() = runTest {
        repository.save(100L, null, null)
        repository.setLanguage(100L, BotLanguage.EN)
        repository.save(200L, null, null)
        repository.setAnnouncementsEnabled(200L, false)

        val chats = repository.findAnnouncementChats().getOrThrow()

        assertEquals(listOf(100L), chats.map { it.chatId })
        assertEquals(BotLanguage.EN, chats.single().language)
    }

    @Test
    fun `setLanguage should not touch the readiness and announcement flags`() = runTest {
        repository.save(100L, null, null)
        repository.setAnnouncementsEnabled(100L, false)
        repository.setReadinessEnabled(100L, false)

        repository.setLanguage(100L, BotLanguage.EN)

        val chat = repository.findById(100L).getOrThrow()
        assertEquals(false, chat?.announcementsEnabled)
        assertEquals(false, chat?.readinessEnabled)
    }
}
