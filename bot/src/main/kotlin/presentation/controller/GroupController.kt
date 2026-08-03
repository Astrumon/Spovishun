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
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.util.displayLabel
import com.ua.astrumon.presentation.util.displayLabelHtml

class GroupController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val autoRegisterService: AutoRegisterService,
    private val messagesProvider: BotMessagesProvider,
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

        val messages = messagesProvider.forChat(chatId)
        return groupService.getAllGroupsWithMembers(chatId).fold(
            onSuccess = { groups ->
                if (groups.isEmpty()) {
                    CommandResponse.Success(messages.group.empty)
                } else {
                    val lines = mutableListOf(messages.group.listHeader)
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
                        lines.add(messages.group.listItem(group.displayLabelHtml(), group.key.escapeHtml(), names.joinToString(", ")))
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

        val messages = messagesProvider.forChat(chatId)
        if (args.isEmpty()) {
            return CommandResponse.Error(messages.group.usageNew)
        }

        val name = args[0].lowercase()

        return groupService.createGroup(chatId, name).fold(
            onSuccess = {
                CommandResponse.Success(messages.group.created(name.escapeHtml()))
            },
            onFailure = { exception ->
                when (exception) {
                    is DuplicateResourceException -> CommandResponse.Error(messages.group.exists(name.escapeHtml()))
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

        val messages = messagesProvider.forChat(chatId)
        if (args.isEmpty()) {
            return CommandResponse.Error(messages.group.usageDel)
        }

        val key = args[0].lowercase()

        return groupService
            .getGroupByKey(chatId, key)
            .flatMap { group ->
                groupService.deleteGroup(chatId, key).map { group.name }
            }.fold(
                onSuccess = { groupName ->
                    CommandResponse.Success(messages.group.deleted(groupName.escapeHtml()))
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

        val messages = messagesProvider.forChat(chatId)
        if (args.isEmpty()) {
            return CommandResponse.Error(messages.group.usageAdd)
        }

        val key = args[0].lowercase()
        val parsed = UsernameInputSanitizer.parseUsernames(args.drop(1).joinToString(" "))

        if (parsed.valid.isEmpty() && parsed.invalid.isEmpty()) {
            return CommandResponse.Error(messages.group.usageAdd)
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
            failed.add("@${token.escapeHtml()}" to messages.group.failureInvalidUsername)
        }

        for (username in parsed.valid) {
            groupService.addMemberToGroup(chatId, key, username).fold(
                onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                onFailure = { exception ->
                    val reason = when (exception) {
                        is ValidationException -> messages.group.failureNotRegistered
                        is DuplicateResourceException -> messages.group.failureAlreadyIn
                        is ResourceNotFoundException -> messages.group.failureNotFound
                        else -> messages.group.failureError
                    }
                    failed.add("@${username.escapeHtml()}" to reason)
                },
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(messages.group.addedTo(succeeded.joinToString(", "), group.name.escapeHtml()))
        if (failed.isNotEmpty()) lines.add(messages.group.notAdded(failed.joinToString(", ") { "${it.first} (${it.second})" }))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun removeUserFromGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        val messages = messagesProvider.forChat(chatId)
        if (args.isEmpty()) {
            return CommandResponse.Error(messages.group.usageRemove)
        }

        val key = args[0].lowercase()
        val parsed = UsernameInputSanitizer.parseUsernames(args.drop(1).joinToString(" "))

        if (parsed.valid.isEmpty() && parsed.invalid.isEmpty()) {
            return CommandResponse.Error(messages.group.usageRemove)
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
            failed.add("@${token.escapeHtml()} (${messages.group.failureInvalidUsername})")
        }

        for (username in parsed.valid) {
            groupService.removeMemberFromGroup(chatId, key, username).fold(
                onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                onFailure = { failed.add("@${username.escapeHtml()}") },
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(messages.group.removedFrom(succeeded.joinToString(", "), group.name.escapeHtml()))
        if (failed.isNotEmpty()) lines.add(messages.group.notFoundInGroup(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun grantRole(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireAdminAccess(chatId, userId)?.let { return it }

        val messages = messagesProvider.forChat(chatId)
        if (args.size < 2) return CommandResponse.Error(messages.group.usageGrant)

        val parsed = UsernameInputSanitizer.parseUsernames(args[0])
        val roleArg = args[1].uppercase()

        val role = runCatching { MemberRole.valueOf(roleArg) }.getOrNull()
            ?: return CommandResponse.Error(messages.error.unknownRole(args[1].escapeHtml()))

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        parsed.invalid.forEach { token ->
            failed.add("@${token.escapeHtml()} (${messages.group.failureInvalidUsername})")
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
        if (succeeded.isNotEmpty()) lines.add(messages.group.rolesGranted(succeeded.joinToString(", "), role.name.lowercase()))
        if (failed.isNotEmpty()) lines.add(messages.group.rolesNotFound(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    // --- Inline-picker listings (spovishun-123) ---

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

    // --- Inline-picker actions by id (spovishun-123) ---

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
    ): GroupWithMembers? = groupService.getAllGroupsWithMembers(chatId).getOrNull()?.firstOrNull { it.id == groupId }

    private suspend fun resolveMemberUsername(
        chatId: Long,
        memberId: Long,
    ): String? = memberService
        .getAllMembersInChat(chatId)
        .getOrNull()
        ?.firstOrNull { it.userId == memberId }
        ?.username

    private fun addFailureReason(
        messages: BotMessages,
        exception: BaseException,
    ): String = when (exception) {
        is ValidationException -> messages.group.failureNotRegistered
        is DuplicateResourceException -> messages.group.failureAlreadyIn
        is ResourceNotFoundException -> messages.group.failureNotFound
        else -> messages.group.failureError
    }
}
