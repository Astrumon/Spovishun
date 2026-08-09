package presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.GroupSettingsController
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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

    /**
     * Stubbed for every test, including the ones that assert nothing is written: an unstubbed call
     * would blow up with a MockK error instead of failing the `coVerify(exactly = 0)` that is
     * actually being tested, and the failure message would point at the wrong thing.
     */
    private lateinit var patch: CapturingSlot<GroupSettingsPatch>

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = GroupSettingsController(groupService, memberService, testMessagesProvider())
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        patch = slot()
        coEvery { groupService.updateGroup(chatId, any(), capture(patch)) } returns ResultContainer.success(Unit)
    }

    @Test
    fun `editGroup should store a single emoji as the group icon`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon=🔥"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("🔥"))
        assertEquals(Patch.Value("🔥"), patch.captured.icon)
    }

    @Test
    fun `editGroup should lowercase the group key`() = runTest {
        controller.editGroup(chatId, userId, listOf("DEVS", "\$icon=🔥"))

        coVerify { groupService.updateGroup(chatId, "devs", any()) }
    }

    @Test
    fun `editGroup should clear the icon when the value is off`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon=off"))

        assertTrue(result is CommandResponse.Success)
        assertEquals(Patch.Value(null), patch.captured.icon)
    }

    @Test
    fun `editGroup should apply every parameter given in one call`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon=🔥", "\$mark=🦀", "\$name=devops"))

        assertTrue(result is CommandResponse.Success)
        assertEquals(Patch.Value("🔥"), patch.captured.icon)
        assertEquals(Patch.Value<PingMark>(PingMark.Custom("🦀")), patch.captured.pingMark)
        assertEquals(Patch.Value("devops"), patch.captured.name)
    }

    @Test
    fun `editGroup should leave unmentioned parameters untouched`() = runTest {
        controller.editGroup(chatId, userId, listOf("devs", "\$mark=🦀"))

        assertEquals(Patch.Untouched, patch.captured.icon)
        assertEquals(Patch.Untouched, patch.captured.name)
    }

    @Test
    fun `editGroup should hide the mark when the value is off`() = runTest {
        controller.editGroup(chatId, userId, listOf("devs", "\$mark=off"))

        assertEquals(Patch.Value<PingMark>(PingMark.Hidden), patch.captured.pingMark)
    }

    @Test
    fun `editGroup should restore the default mark when the value is default`() = runTest {
        controller.editGroup(chatId, userId, listOf("devs", "\$mark=default"))

        assertEquals(Patch.Value<PingMark>(PingMark.Default), patch.captured.pingMark)
    }

    @Test
    fun `editGroup should lowercase a new group name`() = runTest {
        controller.editGroup(chatId, userId, listOf("devs", "\$name=DevOps"))

        assertEquals(Patch.Value("devops"), patch.captured.name)
    }

    @Test
    fun `editGroup should treat off as an ordinary name for the name parameter`() = runTest {
        controller.editGroup(chatId, userId, listOf("devs", "\$name=off"))

        assertEquals(Patch.Value("off"), patch.captured.name)
    }

    @Test
    fun `editGroup should write nothing when one of several values is invalid`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon=🔥", "\$mark=abc"))

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should reject a value that is not a single emoji`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon=abc"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("емодзі"))
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should reject two emoji as an icon`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon=🔥🎯"))

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should reject a name that starts with the parameter prefix`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$name=\$devs"))

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should list supported parameters for an unknown one`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$nope=1"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("\$icon"))
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    /** The whole mitigation for breaking `$icon 🔥`: say which parameter, and show the new form. */
    @Test
    fun `editGroup should point at the equals form when a known parameter has no separator`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon", "🔥"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("\$icon="))
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should reject a duplicated parameter`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$mark=🔥", "\$mark=🦀"))

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should reject a token that is not a parameter`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "abracadabra"))

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should reject a parameter with an empty value`() = runTest {
        val result = controller.editGroup(chatId, userId, listOf("devs", "\$mark="))

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should report the taken name when a rename collides`() = runTest {
        coEvery { groupService.updateGroup(chatId, "devs", any()) } returns ResultContainer.failure(
            DuplicateResourceException("Group", "devops"),
        )

        val result = controller.editGroup(chatId, userId, listOf("devs", "\$name=devops"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("devops"))
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

        val result = controller.editGroup(chatId, userId, listOf("devs", "\$icon=🔥"))

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should return NotFound when group does not exist`() = runTest {
        coEvery { groupService.updateGroup(chatId, "nope", any()) } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "nope"),
        )

        val result = controller.editGroup(chatId, userId, listOf("nope", "\$icon=🔥"))

        assertTrue(result is CommandResponse.NotFound)
        assertEquals("nope", result.identifier)
    }

    @Test
    fun `editGroup should show the settings when no parameters are given`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(
            GroupWithMembers(
                id = 1L,
                chatId = chatId,
                key = "devs",
                name = "devs",
                members = listOf("alice", "bob"),
                icon = "🔥",
                pingMark = PingMark.Custom("🦀"),
            ),
        )

        val result = controller.editGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("🔥"))
        assertTrue(result.message.contains("🦀"))
        assertTrue(result.message.contains("2"), "member count is part of the listing")
        assertTrue(result.message.contains("\$ready-off"), "readiness names the command that edits it")
        coVerify(exactly = 0) { groupService.updateGroup(any(), any(), any()) }
    }

    @Test
    fun `editGroup should render a hidden mark and an absent icon in the listing`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(
            GroupWithMembers(
                id = 1L,
                chatId = chatId,
                key = "devs",
                name = "devs",
                members = emptyList(),
                icon = null,
                pingMark = PingMark.Hidden,
            ),
        )

        val result = controller.editGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("немає"))
        assertTrue(result.message.contains("не показувати"))
    }

    @Test
    fun `editGroup should return NotFound when showing a group that does not exist`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "nope") } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "nope"),
        )

        val result = controller.editGroup(chatId, userId, listOf("nope"))

        assertTrue(result is CommandResponse.NotFound)
        assertEquals("nope", result.identifier)
    }
}
