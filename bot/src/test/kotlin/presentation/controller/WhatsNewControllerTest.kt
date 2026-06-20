package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.ReleaseNote
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
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
    private val chatService: ChatService = mockk()
    private val memberService: MemberService = mockk()
    private lateinit var controller: WhatsNewController

    private val notes = listOf(
        ReleaseNote("2.0.0", "2026-01-01", listOf("New feature")),
        ReleaseNote("1.0.0", "2025-01-01", listOf("Initial release")),
    )

    private val chatId = -100L
    private val adminId = 1L
    private val memberId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = WhatsNewController(releaseNotesService, chatService, memberService)
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
    fun `showLatest should return Silent when notes are empty`() = runTest {
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(emptyList())

        val result = controller.showLatest()

        assertTrue(result is CommandResponse.Silent)
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
    fun `showHistory should return Silent when notes are empty`() = runTest {
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(emptyList())

        val result = controller.showHistory()

        assertTrue(result is CommandResponse.Silent)
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

    @Test
    fun `setAnnouncements should return AccessDenied when user is not admin`() = runTest {
        coEvery { memberService.hasAdminAccess(chatId, memberId) } returns false

        val result = controller.setAnnouncements(chatId, memberId, enabled = true)

        assertTrue(result is CommandResponse.AccessDenied)
    }

    @Test
    fun `setAnnouncements enabled should return Success when user is admin`() = runTest {
        coEvery { memberService.hasAdminAccess(chatId, adminId) } returns true
        coEvery { chatService.setAnnouncementsEnabled(chatId, true) } returns ResultContainer.success(Unit)

        val result = controller.setAnnouncements(chatId, adminId, enabled = true)

        assertTrue(result is CommandResponse.Success)
        assertTrue((result as CommandResponse.Success).message.contains("увімкнено"))
    }

    @Test
    fun `setAnnouncements disabled should return Success when user is admin`() = runTest {
        coEvery { memberService.hasAdminAccess(chatId, adminId) } returns true
        coEvery { chatService.setAnnouncementsEnabled(chatId, false) } returns ResultContainer.success(Unit)

        val result = controller.setAnnouncements(chatId, adminId, enabled = false)

        assertTrue(result is CommandResponse.Success)
        assertTrue((result as CommandResponse.Success).message.contains("вимкнено"))
    }

    @Test
    fun `setAnnouncements should return Error when chatService fails`() = runTest {
        coEvery { memberService.hasAdminAccess(chatId, adminId) } returns true
        coEvery { chatService.setAnnouncementsEnabled(chatId, true) } returns
            ResultContainer.failure(DatabaseException("db error"))

        val result = controller.setAnnouncements(chatId, adminId, enabled = true)

        assertTrue(result is CommandResponse.Error)
    }
}
