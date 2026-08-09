package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.util.displayLabel

/**
 * Backs the inline pickers behind `/addtogroup`, `/removefromgroup`, `/delgroup` and `/grantrole`
 * (spovishun-123): listing what a step may offer, and acting on the id that comes back.
 *
 * Kept apart from [GroupController], which owns the same operations driven by typed arguments
 * (spovishun-172). They are two surfaces onto one domain, not one surface: a picker identifies its
 * target by database id and answers with a listing, a command identifies it by key or `@username`
 * and answers with usage errors. Holding both put fifteen public methods on one class, half of them
 * paired only by name.
 */
class GroupPickerController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    // --- Listings ---

    suspend fun groupsForModeratorPicker(
        chatId: Long,
        userId: Long,
    ): PickerListing {
        requireModeratorAccess(chatId, userId)?.let { return PickerListing.Reject(it) }
        return groupService.getAllGroupsWithMembers(chatId).fold(
            onSuccess = { groups -> PickerListing.Show(groups.map { PickerOption(it.id, it.displayLabel()) }) },
            onFailure = { PickerListing.Reject(CommandResponse.Error(it.userMessage)) },
        )
    }

    suspend fun chatMembersForModeratorPicker(
        chatId: Long,
        userId: Long,
    ): PickerListing {
        requireModeratorAccess(chatId, userId)?.let { return PickerListing.Reject(it) }
        return chatMemberOptions(chatId)
    }

    suspend fun chatMembersForAdminPicker(
        chatId: Long,
        userId: Long,
    ): PickerListing {
        requireAdminAccess(chatId, userId)?.let { return PickerListing.Reject(it) }
        return chatMemberOptions(chatId)
    }

    suspend fun groupMembersForPicker(
        chatId: Long,
        userId: Long,
        groupId: Long,
    ): PickerListing {
        requireModeratorAccess(chatId, userId)?.let { return PickerListing.Reject(it) }
        val group = resolveGroup(chatId, groupId)
            ?: return PickerListing.Reject(CommandResponse.NotFound("Група", groupId.toString()))
        return memberService.getAllMembersInChat(chatId).fold(
            onSuccess = { members ->
                val inGroup = members.filter { it.username in group.members }
                PickerListing.Show(inGroup.map { PickerOption(it.userId, "@${it.username}") })
            },
            onFailure = { PickerListing.Reject(CommandResponse.Error(it.userMessage)) },
        )
    }

    // --- Actions by id ---

    suspend fun deleteGroupById(
        chatId: Long,
        userId: Long,
        groupId: Long,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }
        val messages = messagesProvider.forChat(chatId)
        val group = resolveGroup(chatId, groupId) ?: return CommandResponse.NotFound("Група", groupId.toString())
        return groupService.deleteGroup(chatId, group.key).fold(
            onSuccess = { CommandResponse.Success(messages.group.deleted(group.name.escapeHtml())) },
            onFailure = { CommandResponse.Error(it.userMessage) },
        )
    }

    suspend fun addUserToGroupById(
        chatId: Long,
        userId: Long,
        groupId: Long,
        memberId: Long,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }
        val messages = messagesProvider.forChat(chatId)
        val group = resolveGroup(chatId, groupId) ?: return CommandResponse.NotFound("Група", groupId.toString())
        val username = resolveMemberUsername(chatId, memberId) ?: return CommandResponse.NotFound("Учасник", memberId.toString())
        return groupService.addMemberToGroup(chatId, group.key, username).fold(
            onSuccess = { CommandResponse.Success(messages.group.addedTo("@${username.escapeHtml()}", group.name.escapeHtml())) },
            onFailure = {
                CommandResponse.Success(
                    messages.group.notAdded("@${username.escapeHtml()} (${addFailureReason(messages, it)})"),
                )
            },
        )
    }

    suspend fun removeUserFromGroupById(
        chatId: Long,
        userId: Long,
        groupId: Long,
        memberId: Long,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }
        val messages = messagesProvider.forChat(chatId)
        val group = resolveGroup(chatId, groupId) ?: return CommandResponse.NotFound("Група", groupId.toString())
        val username = resolveMemberUsername(chatId, memberId) ?: return CommandResponse.NotFound("Учасник", memberId.toString())
        return groupService.removeMemberFromGroup(chatId, group.key, username).fold(
            onSuccess = { CommandResponse.Success(messages.group.removedFrom("@${username.escapeHtml()}", group.name.escapeHtml())) },
            onFailure = { CommandResponse.Success(messages.group.notFoundInGroup("@${username.escapeHtml()}")) },
        )
    }

    suspend fun grantRoleById(
        chatId: Long,
        userId: Long,
        memberId: Long,
        role: MemberRole,
    ): CommandResponse {
        requireAdminAccess(chatId, userId)?.let { return it }
        val messages = messagesProvider.forChat(chatId)
        val username = resolveMemberUsername(chatId, memberId) ?: return CommandResponse.NotFound("Учасник", memberId.toString())
        return memberService.setMemberRole(chatId, memberId, role).fold(
            onSuccess = { CommandResponse.Success(messages.group.rolesGranted("@${username.escapeHtml()}", role.name.lowercase())) },
            onFailure = { CommandResponse.Success(messages.group.rolesNotFound("@${username.escapeHtml()}")) },
        )
    }

    private suspend fun chatMemberOptions(chatId: Long): PickerListing = memberService.getAllMembersInChat(chatId).fold(
        onSuccess = { members -> PickerListing.Show(members.map { PickerOption(it.userId, "@${it.username}") }) },
        onFailure = { PickerListing.Reject(CommandResponse.Error(it.userMessage)) },
    )

    private suspend fun resolveGroup(
        chatId: Long,
        groupId: Long,
    ): GroupWithMembers? = groupService.getGroupById(chatId, groupId).getOrNull()

    /**
     * Picker ids are Telegram user ids, not `members.id` — [PickerOption] is built from
     * `member.userId`, so matching on anything else silently resolves nobody.
     */
    private suspend fun resolveMemberUsername(
        chatId: Long,
        memberId: Long,
    ): String? = memberService
        .getAllMembersInChat(chatId)
        .getOrNull()
        ?.firstOrNull { it.userId == memberId }
        ?.username
}
