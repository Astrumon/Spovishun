package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.badge
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages

class GroupController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val autoRegisterService: AutoRegisterService,
) : BaseController(memberService) {
    suspend fun getGroups(
        chatId: Long,
        member: Member,
        userRole: MemberRole,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = member.userId,
            username = member.username,
            firstName = member.firstName,
            userRole = userRole,
        )

        return groupService.getAllGroupsWithMembers(chatId).fold(
            onSuccess = { groups ->
                if (groups.isEmpty()) {
                    CommandResponse.Success(BotMessages.Group.empty)
                } else {
                    val lines = mutableListOf(BotMessages.Group.listHeader)
                    groups.forEach { group ->
                        val names = if (group.members.isNotEmpty()) {
                            group.members.map { username ->
                                val badge = memberService
                                    .getMemberWithChatByUsername(chatId, username)
                                    .fold(onSuccess = { it.role.badge() }, onFailure = { "" })
                                "@${username.escapeHtml()}$badge"
                            }
                        } else {
                            listOf("—")
                        }
                        lines.add(BotMessages.Group.listItem(group.name.escapeHtml(), group.key.escapeHtml(), names.joinToString(", ")))
                    }
                    CommandResponse.Success(lines.joinToString("\n"))
                }
            },
            onFailure = { CommandResponse.Error(it.userMessage) },
        )
    }

    suspend fun createGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageNew)
        }

        val name = args[0].lowercase()

        return groupService.createGroup(chatId, name).fold(
            onSuccess = {
                CommandResponse.Success(BotMessages.Group.created(name.escapeHtml()))
            },
            onFailure = { exception ->
                when (exception) {
                    is DuplicateResourceException -> CommandResponse.Error(BotMessages.Group.exists(name.escapeHtml()))
                    else -> CommandResponse.Error(exception.userMessage)
                }
            },
        )
    }

    suspend fun deleteGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageDel)
        }

        val key = args[0].lowercase()

        return groupService
            .getGroupByKey(chatId, key)
            .flatMap { group ->
                groupService.deleteGroup(chatId, key).map { group.name }
            }.fold(
                onSuccess = { groupName ->
                    CommandResponse.Success(BotMessages.Group.deleted(groupName.escapeHtml()))
                },
                onFailure = { exception ->
                    when (exception) {
                        is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                        else -> CommandResponse.Error(exception.userMessage)
                    }
                },
            )
    }

    suspend fun addUserToGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageAdd)
        }

        val key = args[0].lowercase()
        val parsed = UsernameInputSanitizer.parseUsernames(args.drop(1).joinToString(" "))

        if (parsed.valid.isEmpty() && parsed.invalid.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageAdd)
        }

        val group = groupService.getGroupByKey(chatId, key).fold(
            onSuccess = { it },
            onFailure = { exception ->
                return when (exception) {
                    is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                    else -> CommandResponse.Error(exception.userMessage)
                }
            },
        )

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()

        parsed.invalid.forEach { token ->
            failed.add("@${token.escapeHtml()}" to BotMessages.Group.failureInvalidUsername)
        }

        for (username in parsed.valid) {
            groupService.addMemberToGroup(chatId, key, username).fold(
                onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                onFailure = { exception ->
                    val reason = when (exception) {
                        is ValidationException -> BotMessages.Group.failureNotRegistered
                        is DuplicateResourceException -> BotMessages.Group.failureAlreadyIn
                        is ResourceNotFoundException -> BotMessages.Group.failureNotFound
                        else -> BotMessages.Group.failureError
                    }
                    failed.add("@${username.escapeHtml()}" to reason)
                },
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(BotMessages.Group.addedTo(succeeded.joinToString(", "), group.name.escapeHtml()))
        if (failed.isNotEmpty()) lines.add(BotMessages.Group.notAdded(failed.joinToString(", ") { "${it.first} (${it.second})" }))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun removeUserFromGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageRemove)
        }

        val key = args[0].lowercase()
        val parsed = UsernameInputSanitizer.parseUsernames(args.drop(1).joinToString(" "))

        if (parsed.valid.isEmpty() && parsed.invalid.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageRemove)
        }

        val group = groupService.getGroupByKey(chatId, key).fold(
            onSuccess = { it },
            onFailure = { exception ->
                return when (exception) {
                    is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                    else -> CommandResponse.Error(exception.userMessage)
                }
            },
        )

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        parsed.invalid.forEach { token ->
            failed.add("@${token.escapeHtml()} (${BotMessages.Group.failureInvalidUsername})")
        }

        for (username in parsed.valid) {
            groupService.removeMemberFromGroup(chatId, key, username).fold(
                onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                onFailure = { failed.add("@${username.escapeHtml()}") },
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(BotMessages.Group.removedFrom(succeeded.joinToString(", "), group.name.escapeHtml()))
        if (failed.isNotEmpty()) lines.add(BotMessages.Group.notFoundInGroup(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun grantRole(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireAdminAccess(chatId, userId)?.let { return it }

        if (args.size < 2) return CommandResponse.Error(BotMessages.Group.usageGrant)

        val parsed = UsernameInputSanitizer.parseUsernames(args[0])
        val roleArg = args[1].uppercase()

        val role = runCatching { MemberRole.valueOf(roleArg) }.getOrNull()
            ?: return CommandResponse.Error(BotMessages.Error.unknownRole(args[1].escapeHtml()))

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        parsed.invalid.forEach { token ->
            failed.add("@${token.escapeHtml()} (${BotMessages.Group.failureInvalidUsername})")
        }

        for (username in parsed.valid) {
            memberService
                .getMemberByUsername(username)
                .flatMap { member -> memberService.setMemberRole(chatId, member.userId, role) }
                .fold(
                    onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                    onFailure = { failed.add("@${username.escapeHtml()}") },
                )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(BotMessages.Group.rolesGranted(succeeded.joinToString(", "), role.name.lowercase()))
        if (failed.isNotEmpty()) lines.add(BotMessages.Group.rolesNotFound(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    // --- Inline-picker listings (spovishun-123) ---

    suspend fun groupsForModeratorPicker(
        chatId: Long,
        userId: Long,
    ): PickerListing {
        requireModeratorAccess(chatId, userId)?.let { return PickerListing.Reject(it) }
        return groupService.getAllGroupsWithMembers(chatId).fold(
            onSuccess = { groups -> PickerListing.Show(groups.map { PickerOption(it.id, it.name) }) },
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

    // --- Inline-picker actions by id (spovishun-123) ---

    suspend fun deleteGroupById(
        chatId: Long,
        userId: Long,
        groupId: Long,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }
        val group = resolveGroup(chatId, groupId) ?: return CommandResponse.NotFound("Група", groupId.toString())
        return groupService.deleteGroup(chatId, group.key).fold(
            onSuccess = { CommandResponse.Success(BotMessages.Group.deleted(group.name.escapeHtml())) },
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
        val group = resolveGroup(chatId, groupId) ?: return CommandResponse.NotFound("Група", groupId.toString())
        val username = resolveMemberUsername(chatId, memberId) ?: return CommandResponse.NotFound("Учасник", memberId.toString())
        return groupService.addMemberToGroup(chatId, group.key, username).fold(
            onSuccess = { CommandResponse.Success(BotMessages.Group.addedTo("@${username.escapeHtml()}", group.name.escapeHtml())) },
            onFailure = { CommandResponse.Success(BotMessages.Group.notAdded("@${username.escapeHtml()} (${addFailureReason(it)})")) },
        )
    }

    suspend fun removeUserFromGroupById(
        chatId: Long,
        userId: Long,
        groupId: Long,
        memberId: Long,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }
        val group = resolveGroup(chatId, groupId) ?: return CommandResponse.NotFound("Група", groupId.toString())
        val username = resolveMemberUsername(chatId, memberId) ?: return CommandResponse.NotFound("Учасник", memberId.toString())
        return groupService.removeMemberFromGroup(chatId, group.key, username).fold(
            onSuccess = { CommandResponse.Success(BotMessages.Group.removedFrom("@${username.escapeHtml()}", group.name.escapeHtml())) },
            onFailure = { CommandResponse.Success(BotMessages.Group.notFoundInGroup("@${username.escapeHtml()}")) },
        )
    }

    suspend fun grantRoleById(
        chatId: Long,
        userId: Long,
        memberId: Long,
        role: MemberRole,
    ): CommandResponse {
        requireAdminAccess(chatId, userId)?.let { return it }
        val username = resolveMemberUsername(chatId, memberId) ?: return CommandResponse.NotFound("Учасник", memberId.toString())
        return memberService.setMemberRole(chatId, memberId, role).fold(
            onSuccess = { CommandResponse.Success(BotMessages.Group.rolesGranted("@${username.escapeHtml()}", role.name.lowercase())) },
            onFailure = { CommandResponse.Success(BotMessages.Group.rolesNotFound("@${username.escapeHtml()}")) },
        )
    }

    private suspend fun chatMemberOptions(chatId: Long): PickerListing = memberService.getAllMembersInChat(chatId).fold(
        onSuccess = { members -> PickerListing.Show(members.map { PickerOption(it.userId, "@${it.username}") }) },
        onFailure = { PickerListing.Reject(CommandResponse.Error(it.userMessage)) },
    )

    private suspend fun resolveGroup(
        chatId: Long,
        groupId: Long,
    ): GroupWithMembers? = groupService.getAllGroupsWithMembers(chatId).getOrNull()?.firstOrNull { it.id == groupId }

    private suspend fun resolveMemberUsername(
        chatId: Long,
        memberId: Long,
    ): String? = memberService
        .getAllMembersInChat(chatId)
        .getOrNull()
        ?.firstOrNull { it.userId == memberId }
        ?.username

    private fun addFailureReason(exception: BaseException): String = when (exception) {
        is ValidationException -> BotMessages.Group.failureNotRegistered
        is DuplicateResourceException -> BotMessages.Group.failureAlreadyIn
        is ResourceNotFoundException -> BotMessages.Group.failureNotFound
        else -> BotMessages.Group.failureError
    }
}
