package presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.GroupController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupControllerHtmlEscapeTest {
    private val groupService: GroupService = mockk()
    private val memberService: MemberService = mockk()
    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var groupController: GroupController

    private val chatId = 123L
    private val userId = 456L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        groupController = GroupController(groupService, memberService, autoRegisterService, testMessagesProvider())
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { memberService.hasAdminAccess(chatId, userId) } returns true
    }

    @Test
    fun `createGroup should escape HTML special chars in group name on success`() = runTest {
        val maliciousName = "<b>hack</b>"
        val group = Group(1L, chatId, maliciousName, emptyList())
        coEvery { groupService.createGroup(chatId, maliciousName) } returns ResultContainer.success(group)

        val result = groupController.createGroup(chatId, userId, listOf(maliciousName))

        assertTrue(result is CommandResponse.Success)
        assertFalse(result.message.contains("<b>hack</b>"), "Raw HTML must not appear in output")
        assertTrue(result.message.contains("&lt;b&gt;hack&lt;/b&gt;"), "HTML must be escaped")
    }

    @Test
    fun `createGroup should escape HTML special chars in duplicate error message`() = runTest {
        val maliciousName = "<b>hack</b>"
        coEvery { groupService.createGroup(chatId, maliciousName) } returns
            ResultContainer.failure(DuplicateResourceException("Group", maliciousName))

        val result = groupController.createGroup(chatId, userId, listOf(maliciousName))

        assertTrue(result is CommandResponse.Error)
        assertFalse(result.message.contains("<b>hack</b>"), "Raw HTML must not appear in error message")
        assertTrue(result.message.contains("&lt;b&gt;hack&lt;/b&gt;"))
    }

    @Test
    fun `deleteGroup should escape HTML in success message`() = runTest {
        val maliciousName = "<script>alert(1)</script>"
        val group = GroupWithMembers(1L, chatId, "key", maliciousName, emptyList())
        coEvery { groupService.getGroupByKey(chatId, any()) } returns ResultContainer.success(group)
        coEvery { groupService.deleteGroup(chatId, any()) } returns ResultContainer.success(Unit)

        val result = groupController.deleteGroup(chatId, userId, listOf("key"))

        assertTrue(result is CommandResponse.Success)
        assertFalse(result.message.contains("<script>"))
        assertTrue(result.message.contains("&lt;script&gt;"))
    }

    @Test
    fun `grantRole should escape raw role arg in error message`() = runTest {
        val result = groupController.grantRole(chatId, userId, listOf("@user", "<b>notarole</b>"))

        assertTrue(result is CommandResponse.Error)
        assertFalse(result.message.contains("<b>notarole</b>"), "Raw HTML in role arg must be escaped")
        assertTrue(result.message.contains("&lt;b&gt;notarole&lt;/b&gt;"))
    }

    @Test
    fun `createGroup should handle ampersand in group name`() = runTest {
        val nameWithAmp = "dev&ops"
        val group = Group(1L, chatId, nameWithAmp, emptyList())
        coEvery { groupService.createGroup(chatId, nameWithAmp) } returns ResultContainer.success(group)

        val result = groupController.createGroup(chatId, userId, listOf(nameWithAmp))

        assertTrue(result is CommandResponse.Success)
        assertFalse(result.message.contains("dev&ops"), "Raw & must be escaped")
        assertTrue(result.message.contains("dev&amp;ops"))
    }

    @Test
    fun `getGroups should escape group name and key`() = runTest {
        val groups = listOf(GroupWithMembers(1L, chatId, "<evil>", "<key>", emptyList()))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(groups)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.success(mockk())

        val result = groupController.getGroups(
            chatId,
            mockk(relaxed = true),
            MemberRole.MEMBER,
        )

        assertTrue(result is CommandResponse.Success)
        assertFalse(result.message.contains("<evil>"))
        assertTrue(result.message.contains("&lt;evil&gt;"))
    }
}
