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
                    CommandResponse.Success("<b>Немає груп</b>. Створи: /newgroup &lt;назва&gt;")
                } else {
                    val lines = mutableListOf("📋 <b>Групи:</b>")
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
                        lines.add("• <b>${group.name.escapeHtml()}</b> (/ping ${group.key.escapeHtml()}): ${names.joinToString(", ")}")
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
            return CommandResponse.Error("Не правильно використовуєш команду, спробуй: /newgroup &lt;назва&gt;")
        }

        val name = args[0].lowercase()

        return groupService.createGroup(chatId, name).fold(
            onSuccess = {
                CommandResponse.Success("Група <b>${name.escapeHtml()}</b> створена!\nВиклик: /ping ${name.escapeHtml()}")
            },
            onFailure = { exception ->
                when (exception) {
                    is DuplicateResourceException -> CommandResponse.Error("Група <b>${name.escapeHtml()}</b> вже існує.")
                    else -> CommandResponse.Error(exception.userMessage)
                }
            }
        )
    }

    suspend fun deleteGroup(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error("Використання: /delgroup &lt;назва&gt;")
        }

        val key = args[0].lowercase()

        return groupService.getGroupByKey(chatId, key).flatMap { group ->
            groupService.deleteGroup(chatId, key).map { group.name }
        }.fold(
            onSuccess = { groupName ->
                CommandResponse.Success("Група <b>${groupName.escapeHtml()}</b> видалена.")
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
            return CommandResponse.Error("Використання: /addtogroup &lt;назва&gt; @username")
        }

        val key = args[0].lowercase()
        val usernames = UserListParser.parseUserList(args.drop(1).joinToString(" "))

        if (usernames.isEmpty()) {
            return CommandResponse.Error("Використання: /addtogroup &lt;назва&gt; @username")
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
                        is ValidationException -> "не зареєстровано"
                        is DuplicateResourceException -> "вже в групі"
                        is ResourceNotFoundException -> "не знайдено"
                        else -> "помилка"
                    }
                    failed.add("@${username.escapeHtml()}" to reason)
                }
            )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add("${succeeded.joinToString(", ")} додано до <b>${group.name.escapeHtml()}</b>.")
        if (failed.isNotEmpty()) lines.add("Не додано: ${failed.joinToString(", ") { "${it.first} (${it.second})" }}.")
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun removeUserFromGroup(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.isEmpty()) {
            return CommandResponse.Error("Використання: /removefromgroup &lt;назва&gt; @username")
        }

        val key = args[0].lowercase()
        val usernames = UserListParser.parseUserList(args.drop(1).joinToString(" "))

        if (usernames.isEmpty()) {
            return CommandResponse.Error("Використання: /removefromgroup &lt;назва&gt; @username")
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
        if (succeeded.isNotEmpty()) lines.add("${succeeded.joinToString(", ")} видалено з <b>${group.name.escapeHtml()}</b>.")
        if (failed.isNotEmpty()) lines.add("Не знайдено в групі: ${failed.joinToString(", ")}.")
        return CommandResponse.Success(lines.joinToString("\n"))
    }

    suspend fun grantRole(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireAdminAccess(chatId, userId)?.let { return it }

        if (args.size < 2) return CommandResponse.Error("Використання: /grantrole @user1,@user2 moderator|admin|member")

        val usernames = UserListParser.parseUserList(args[0])
        val roleArg = args[1].uppercase()

        val role = runCatching { MemberRole.valueOf(roleArg) }.getOrNull()
            ?: return CommandResponse.Error("Невідома роль: ${args[1].escapeHtml()}. Доступні: moderator, admin, member")

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
        if (succeeded.isNotEmpty()) lines.add("${succeeded.joinToString(", ")} отримали роль ${role.name.lowercase()}.")
        if (failed.isNotEmpty()) lines.add("Не знайдено: ${failed.joinToString(", ")}.")
        return CommandResponse.Success(lines.joinToString("\n"))
    }
}
