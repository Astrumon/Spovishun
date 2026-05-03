package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.util.UserListParser
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.badge
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
class GroupController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val autoRegisterService: AutoRegisterService,
) : BaseController(memberService) {

    suspend fun getGroups(chatId: Long, member: Member, userRole: MemberRole): CommandResponse {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = member.userId,
            username = member.username,
            firstName = member.firstName,
            userRole = userRole
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
                                val badge = memberService.getMemberWithChatByUsername(chatId, username)
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
            onFailure = { CommandResponse.Error(it.userMessage) }
        )
    }

    suspend fun createGroup(chatId: Long, userId: Long, args: List<String>): CommandResponse {
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
            }
        )
    }

    suspend fun deleteGroup(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageDel)
        }

        val key = args[0].lowercase()

        return groupService.getGroupByKey(chatId, key).flatMap { group ->
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
            }
        )
    }

    suspend fun addUserToGroup(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageAdd)
        }

        val key = args[0].lowercase()
        val usernames = UserListParser.parseUserList(args.drop(1).joinToString(" "))

        if (usernames.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageAdd)
        }

        val group = groupService.getGroupByKey(chatId, key).fold(
            onSuccess = { it },
            onFailure = { exception ->
                return when (exception) {
                    is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                    else -> CommandResponse.Error(exception.userMessage)
                }
            }
        )

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()

        for (username in usernames) {
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
                }
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(BotMessages.Group.addedTo(succeeded.joinToString(", "), group.name.escapeHtml()))
        if (failed.isNotEmpty()) lines.add(BotMessages.Group.notAdded(failed.joinToString(", ") { "${it.first} (${it.second})" }))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun removeUserFromGroup(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageRemove)
        }

        val key = args[0].lowercase()
        val usernames = UserListParser.parseUserList(args.drop(1).joinToString(" "))

        if (usernames.isEmpty()) {
            return CommandResponse.Error(BotMessages.Group.usageRemove)
        }

        val group = groupService.getGroupByKey(chatId, key).fold(
            onSuccess = { it },
            onFailure = { exception ->
                return when (exception) {
                    is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                    else -> CommandResponse.Error(exception.userMessage)
                }
            }
        )

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (username in usernames) {
            groupService.removeMemberFromGroup(chatId, key, username).fold(
                onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                onFailure = { failed.add("@${username.escapeHtml()}") }
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(BotMessages.Group.removedFrom(succeeded.joinToString(", "), group.name.escapeHtml()))
        if (failed.isNotEmpty()) lines.add(BotMessages.Group.notFoundInGroup(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun grantRole(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireAdminAccess(chatId, userId)?.let { return it }

        if (args.size < 2) return CommandResponse.Error(BotMessages.Group.usageGrant)

        val usernames = UserListParser.parseUserList(args[0])
        val roleArg = args[1].uppercase()

        val role = runCatching { MemberRole.valueOf(roleArg) }.getOrNull()
            ?: return CommandResponse.Error(BotMessages.Error.unknownRole(args[1].escapeHtml()))

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (username in usernames) {
            memberService.getMemberByUsername(username)
                .flatMap { member -> memberService.setMemberRole(chatId, member.userId, role) }
                .fold(
                    onSuccess = { succeeded.add("@${username.escapeHtml()}") },
                    onFailure = { failed.add("@${username.escapeHtml()}") }
                )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add(BotMessages.Group.rolesGranted(succeeded.joinToString(", "), role.name.lowercase()))
        if (failed.isNotEmpty()) lines.add(BotMessages.Group.rolesNotFound(failed.joinToString(", ")))
        return CommandResponse.Success(lines.joinToString("\n"))
    }
}
