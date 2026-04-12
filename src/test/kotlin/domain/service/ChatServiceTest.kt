package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Chat
import com.ua.astrumon.domain.repository.ChatRepository
import com.ua.astrumon.domain.service.ChatService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatServiceTest {

    private val chatRepository: ChatRepository = mockk()
    private lateinit var chatService: ChatService

    @BeforeTest
    fun setup() {
        clearAllMocks()
        chatService = ChatService(chatRepository)
    }

    @Test
    fun `ensureChat should return existing chat when found`() = runTest {
        // Given
        val chatId = 123L
        val existingChat = Chat(chatId, "Test Group", "group", Clock.System.now())
        coEvery { chatRepository.findById(chatId) } returns ResultContainer.success(existingChat)

        // When
        val result = chatService.ensureChat(chatId, "Test Group", "group")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(existingChat, result.getOrThrow())
        coVerify(exactly = 0) { chatRepository.save(any(), any(), any()) }
    }

    @Test
    fun `ensureChat should save and return new chat when not found`() = runTest {
        // Given
        val chatId = 123L
        val newChat = Chat(chatId, "Test Group", "group", Clock.System.now())
        coEvery { chatRepository.findById(chatId) } returns ResultContainer.success(null)
        coEvery { chatRepository.save(chatId, "Test Group", "group") } returns ResultContainer.success(newChat)

        // When
        val result = chatService.ensureChat(chatId, "Test Group", "group")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(newChat, result.getOrThrow())
        coVerify { chatRepository.save(chatId, "Test Group", "group") }
    }

    @Test
    fun `ensureChat should propagate failure from findById`() = runTest {
        // Given
        val chatId = 123L
        coEvery { chatRepository.findById(chatId) } returns ResultContainer.failure(DatabaseException("DB error"))

        // When
        val result = chatService.ensureChat(chatId, "Test Group", "group")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }

    @Test
    fun `ensureChat should propagate failure from save`() = runTest {
        // Given
        val chatId = 123L
        coEvery { chatRepository.findById(chatId) } returns ResultContainer.success(null)
        coEvery { chatRepository.save(chatId, "Test Group", "group") } returns ResultContainer.failure(DatabaseException("DB error"))

        // When
        val result = chatService.ensureChat(chatId, "Test Group", "group")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }
}
