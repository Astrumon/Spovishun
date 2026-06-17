package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.ReleaseNote
import com.ua.astrumon.domain.repository.ReleaseNotesRepository
import com.ua.astrumon.domain.service.ReleaseNotesService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNotesServiceTest {
    private val releaseNotesRepository: ReleaseNotesRepository = mockk()
    private lateinit var releaseNotesService: ReleaseNotesService

    @BeforeTest
    fun setup() {
        clearAllMocks()
        releaseNotesService = ReleaseNotesService(releaseNotesRepository)
    }

    @Test
    fun `getAll should delegate to repository and return its notes`() = runTest {
        // Given
        val notes = listOf(
            ReleaseNote("2.0.0", "2026-01-01", listOf("New feature")),
            ReleaseNote("1.0.0", "2025-01-01", listOf("Initial release")),
        )
        coEvery { releaseNotesRepository.getAll() } returns ResultContainer.success(notes)

        // When
        val result = releaseNotesService.getAll()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(notes, result.getOrThrow())
        coVerify { releaseNotesRepository.getAll() }
    }

    @Test
    fun `getAll should propagate failure from repository`() = runTest {
        // Given
        coEvery { releaseNotesRepository.getAll() } returns ResultContainer.failure(DatabaseException("not found"))

        // When
        val result = releaseNotesService.getAll()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }
}
