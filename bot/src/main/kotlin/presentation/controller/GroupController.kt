package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
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

    /**
     * `/newgroup <name> [$icon=… $mark=…]` — creates the group and, in the same transaction, the
     * settings the same parameters set in `/editg` (spovishun-182).
     *
     * Nothing is created until every parameter has validated: a rejected token leaves the chat
     * exactly as it was, so the user retypes one command rather than deleting a half-configured group.
     */
    suspend fun createGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        val messages = messagesProvider.forChat(chatId)
        // A name starting with `$` would be read back as a parameter by every later command, so
        // `/newgroup $icon=🔥` is a missing name rather than a group called "$icon=🔥".
        if (args.isEmpty() || args[0].startsWith(GroupParam.PREFIX)) {
            return CommandResponse.Error(messages.group.usageNew)
        }

        val name = args[0].lowercase()
        val settings = when (val built = settingsFor(args.drop(1), messages)) {
            is CreateSettings.Rejected -> return built.response
            is CreateSettings.Ready -> built.patch
        }

        return groupService.createGroup(chatId, name, settings).fold(
            onSuccess = {
                CommandResponse.Success(
                    GroupSettingsPresenter.created(messages.group.created(name.escapeHtml()), settings, messages),
                )
            },
            onFailure = { exception ->
                when (exception) {
                    is DuplicateResourceException -> CommandResponse.Error(messages.group.exists(name.escapeHtml()))
                    else -> CommandResponse.Error(exception.userMessage)
                }
            },
        )
    }

    /** The tokens after the positional name, as a patch — or the reply saying why they are not one. */
    private fun settingsFor(
        tokens: List<String>,
        messages: BotMessages,
    ): CreateSettings = when (val parsed = GroupParamParser.parse(tokens)) {
        // No parameters is the pre-spovishun-182 call: create the group, touch no setting.
        is GroupParamParseResult.Show -> CreateSettings.Ready(GroupSettingsPatch())
        is GroupParamParseResult.Failure ->
            CreateSettings.Rejected(CommandResponse.Error(GroupSettingsPresenter.parseFailure(parsed, messages)))
        is GroupParamParseResult.Edit -> fromValues(parsed.values, messages)
    }

    private fun fromValues(
        values: Map<GroupParam, String>,
        messages: BotMessages,
    ): CreateSettings {
        // `$name` is a real parameter, just not this command's: here the name already arrived as the
        // positional argument, so accepting it would mean two names and no rule for which one wins.
        if (values.containsKey(GroupParam.NAME)) {
            return CreateSettings.Rejected(CommandResponse.Error(messages.group.nameParamNotAllowed))
        }
        return when (val built = GroupParamPatchBuilder.build(values, messages)) {
            is PatchResult.Invalid -> CreateSettings.Rejected(CommandResponse.Error(built.message))
            is PatchResult.Built -> CreateSettings.Ready(built.patch)
        }
    }

    private sealed interface CreateSettings {
        data class Ready(
            val patch: GroupSettingsPatch,
        ) : CreateSettings

        data class Rejected(
            val response: CommandResponse,
        ) : CreateSettings
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
