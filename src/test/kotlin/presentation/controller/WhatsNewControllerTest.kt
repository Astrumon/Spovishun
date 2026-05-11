package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.ReleaseNote
import com.ua.astrumon.domain.service.ReleaseNotesService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.WhatsNewController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class WhatsNewControllerTest {

    private val releaseNotesService: ReleaseNotesService = mockk()
    private lateinit var controller: WhatsNewController

    private val notes = listOf(
        ReleaseNote("2.0.0", "2026-01-01", listOf("New feature")),
        ReleaseNote("1.0.0", "2025-01-01", listOf("Initial release")),
    )

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = WhatsNewController(releaseNotesService)
    }

    @Test
    fun `showLatest should return Success with first note content`() = runTest {
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(notes)

        val result = controller.showLatest()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("2.0.0"))
    }

    @Test
    fun `showLatest should not include older versions`() = runTest {
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(notes)

        val result = controller.showLatest()

        assertTrue(result is CommandResponse.Success)
        assertTrue(!result.message.contains("1.0.0"))
    }

    @Test
    fun `showLatest should return Error when service fails`() = runTest {
        coEvery { releaseNotesService.getAll() } returns
            ResultContainer.failure(DatabaseException("classpath read failed"))

        val result = controller.showLatest()

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `showHistory should return Success with all versions`() = runTest {
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(notes)

        val result = controller.showHistory()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("2.0.0"))
        assertTrue(result.message.contains("1.0.0"))
    }

    @Test
    fun `showHistory should return Error when service fails`() = runTest {
        coEvery { releaseNotesService.getAll() } returns
            ResultContainer.failure(DatabaseException("classpath read failed"))

        val result = controller.showHistory()

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `showLatest should return Success for single-entry list`() = runTest {
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(listOf(notes.first()))

        val result = controller.showLatest()

        assertTrue(result is CommandResponse.Success)
    }
}
