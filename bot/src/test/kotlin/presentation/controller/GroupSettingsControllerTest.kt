package presentation.controller

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.GroupSettingsController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupSettingsControllerTest {
    private val groupService: GroupService = mockk()
    private val memberService: MemberService = mockk()
    private lateinit var controller: GroupSettingsController

    private val chatId = 123L
    private val userId = 456L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = GroupSettingsController(groupService, memberService, testMessagesProvider())
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
    }

    @Test
    fun `editGroup should store a single emoji as the group icon`() = runTest {
        coEvery { groupService.setIcon(chatId, "devs", "🔥") } returns ResultContainer.success(Unit)

        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon", "🔥"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("🔥"))
        coVerify { groupService.setIcon(chatId, "devs", "🔥") }
    }

    @Test
    fun `editGroup should lowercase the group key`() = runTest {
        coEvery { groupService.setIcon(chatId, "devs", "🔥") } returns ResultContainer.success(Unit)

        controller.editGroup(chatId, userId, listOf("DEVS", "\$icon", "🔥"))

        coVerify { groupService.setIcon(chatId, "devs", "🔥") }
    }

    @Test
    fun `editGroup should clear the icon when the value is off`() = runTest {
        coEvery { groupService.setIcon(chatId, "devs", null) } returns ResultContainer.success(Unit)

        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon", "off"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("прибрано"))
        coVerify { groupService.setIcon(chatId, "devs", null) }
    }

    @Test
    fun `editGroup should reject a value that is not a single emoji`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon", "abc"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("емодзі"))
        coVerify(exactly = 0) { groupService.setIcon(any(), any(), any()) }
    }

    @Test
    fun `editGroup should reject two emoji as an icon`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon", "🔥🎯"))

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { groupService.setIcon(any(), any(), any()) }
    }

    @Test
    fun `editGroup should list supported parameters for an unknown one`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$nope", "1"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("\$icon"))
        coVerify(exactly = 0) { groupService.setIcon(any(), any(), any()) }
    }

    @Test
    fun `editGroup should return usage error when the value is missing`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/editg"))
    }

    @Test
    fun `editGroup should return usage error when no args`() = runTest {
        val result = controller.editGroup(chatId, userId, emptyList())

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/editg"))
    }

    @Test
    fun `editGroup should return AccessDenied when caller is regular member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon", "🔥"))

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { groupService.setIcon(any(), any(), any()) }
    }

    @Test
    fun `editGroup should return NotFound when group does not exist`() = runTest {
        coEvery { groupService.setIcon(chatId, "nope", "🔥") } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "nope"),
        )

        val result = controller.editGroup(chatId, userId, listOf("nope", "\$icon", "🔥"))

        assertTrue(result is CommandResponse.NotFound)
        assertEquals("nope", result.identifier)
    }
}
