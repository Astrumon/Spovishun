package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.badge
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.util.displayLabelHtml

/**
 * Backs the group commands driven by typed arguments — `/groups`, `/newgroup`, `/delgroup`,
 * `/addtogroup`, `/removefromgroup`, `/grantrole`.
 *
 * The same operations driven by an inline picker live in [GroupPickerController] (spovishun-172):
 * there a target is a database id and the answer is a listing, here it is a key or an `@username`
 * and the answer is a usage error.
 */
class GroupController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    suspend fun getGroups(chatId: Long): CommandResponse {
        val messages = messagesProvider.forChat(chatId)
        return groupService.getAllGroupsWithMembers(chatId).fold(
            onSuccess = { groups ->
                if (groups.isEmpty()) {
                    CommandResponse.Success(messages.group.empty)
                } else {
                    val lines = mutableListOf(messages.group.listHeader)
                    groups.forEach { group -> lines.add(groupLine(chatId, messages, group)) }
                    CommandResponse.Success(lines.joinToString("\n"))
                }
            },
            onFailure = { CommandResponse.Error(it.userMessage) },
        )
    }

    private suspend fun groupLine(
        chatId: Long,
        messages: BotMessages,
        group: GroupWithMembers,
    ): String {
        val names = if (group.members.isEmpty()) {
            listOf("—")
        } else {
            group.members.map { username ->
                val badge = memberService
                    .getMemberWithChatByUsername(chatId, username)
                    .fold(onSuccess = { it.role.badge() }, onFailure = { "" })
                "@${username.escapeHtml()}$badge"
            }
        }
        return messages.group.listItem(group.displayLabelHtml(), group.key.escapeHtml(), names.joinToString(", "))
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
        val messages = messagesProvider.forChat(chatId)
        val target = when (val resolved = resolveMembershipTarget(chatId, userId, args, messages.group.usageAdd)) {
            is MembershipTarget.Rejected -> return resolved.response
            is MembershipTarget.Resolved -> resolved
        }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        target.usernames.invalid.forEach { token ->
            failed.add("@${token.escapeHtml()} (${messages.group.failureInvalidUsername})")
        }

        for (username in target.usernames.valid) {
            groupService.addMemberToGroup(chatId, target.group.key, username).fold(
                onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                onFailure = { failed.add("@${username.escapeHtml()} (${addFailureReason(messages, it)})") },
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(messages.group.addedTo(succeeded.joinToString(", "), target.group.name.escapeHtml()))
        if (failed.isNotEmpty()) lines.add(messages.group.notAdded(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun removeUserFromGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        val messages = messagesProvider.forChat(chatId)
        val target = when (val resolved = resolveMembershipTarget(chatId, userId, args, messages.group.usageRemove)) {
            is MembershipTarget.Rejected -> return resolved.response
            is MembershipTarget.Resolved -> resolved
        }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        target.usernames.invalid.forEach { token ->
            failed.add("@${token.escapeHtml()} (${messages.group.failureInvalidUsername})")
        }

        for (username in target.usernames.valid) {
            groupService.removeMemberFromGroup(chatId, target.group.key, username).fold(
                onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                onFailure = { failed.add("@${username.escapeHtml()}") },
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) {
            lines.add(messages.group.removedFrom(succeeded.joinToString(", "), target.group.name.escapeHtml()))
        }
        if (failed.isNotEmpty()) lines.add(messages.group.notFoundInGroup(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    /**
     * `<group> @user…` resolved once: the role gate, the usage checks and the group lookup that
     * `/addtogroup` and `/removefromgroup` open with are identical, and each was four early returns.
     */
    private suspend fun resolveMembershipTarget(
        chatId: Long,
        userId: Long,
        args: List<String>,
        usage: String,
    ): MembershipTarget {
        requireModeratorAccess(chatId, userId)?.let { return MembershipTarget.Rejected(it) }
        if (args.isEmpty()) return MembershipTarget.Rejected(CommandResponse.Error(usage))

        val key = args[0].lowercase()
        val parsed = UsernameInputSanitizer.parseUsernames(args.drop(1).joinToString(" "))
        if (parsed.valid.isEmpty() && parsed.invalid.isEmpty()) {
            return MembershipTarget.Rejected(CommandResponse.Error(usage))
        }

        return groupService.getGroupByKey(chatId, key).fold(
            onSuccess = { MembershipTarget.Resolved(it, parsed) },
            onFailure = { exception ->
                MembershipTarget.Rejected(
                    when (exception) {
                        is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                        else -> CommandResponse.Error(exception.userMessage)
                    },
                )
            },
        )
    }

    /** Either a group and the usernames to move in or out of it, or the reply that says why not. */
    private sealed interface MembershipTarget {
        data class Resolved(
            val group: GroupWithMembers,
            val usernames: UsernameInputSanitizer.ParseResult,
        ) : MembershipTarget

        data class Rejected(
            val response: CommandResponse,
        ) : MembershipTarget
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
}
