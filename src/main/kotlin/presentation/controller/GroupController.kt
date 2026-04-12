package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.BusinessException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.badge
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.MemberService
import org.slf4j.LoggerFactory

class GroupController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val autoRegisterService: AutoRegisterService,
) : BaseController(memberService) {
    private val logger = LoggerFactory.getLogger(GroupController::class.java)

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
                                val badge = memberService.getMemberByUsername(username)
                                    .fold(onSuccess = { it.role.badge() }, onFailure = { "" })
                                "@$username$badge"
                            }
                        } else {
                            listOf("—")
                        }
                        lines.add("• <b>${group.name}</b> (/ping ${group.key}): ${names.joinToString(", ")}")
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
                CommandResponse.Success("Група <b>$name</b> створена!\nВиклик: /ping $name")
            },
            onFailure = { exception ->
                when (exception) {
                    is DuplicateResourceException -> CommandResponse.Error("Група <b>$name</b> вже існує.")
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
                CommandResponse.Success("Група <b>$groupName</b> видалена.")
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

        if (args.size < 2) {
            return CommandResponse.Error("Використання: /addtogroup &lt;назва&gt; @username")
        }

        val key = args[0].lowercase()
        val username = args[1].removePrefix("@")
        logger.info("Processing addUserToGroup with key: '$key' and username: '$username'")

        return groupService.getGroupByKey(chatId, key).flatMap { group ->
            groupService.addMemberToGroup(chatId, key, username).map { group }
        }.fold(
            onSuccess = { group ->
                CommandResponse.Success("<b>$username</b> додано до <b>${group.name}</b>.")
            },
            onFailure = { exception ->
                when (exception) {
                    is ValidationException -> CommandResponse.Error("Неможливо додати @$username. Перевірте чи існує такий користувач")
                    is ResourceNotFoundException -> {
                        if (exception.resource == "Group") CommandResponse.NotFound("Група", key)
                        else CommandResponse.NotFound("Користувач", username)
                    }
                    is DuplicateResourceException -> CommandResponse.Error("@$username вже в групі <b>$key</b>.")
                    else -> CommandResponse.Error(exception.userMessage)
                }
            }
        )
    }

    suspend fun removeUserFromGroup(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.size < 2) {
            return CommandResponse.Error("Використання: /removefromgroup &lt;назва&gt; @username")
        }

        val key = args[0].lowercase()
        val username = args[1].removePrefix("@")

        return groupService.getGroupByKey(chatId, key).flatMap { group ->
            groupService.removeMemberFromGroup(chatId, key, username).map { group }
        }.fold(
            onSuccess = { group ->
                CommandResponse.Success("<b>$username</b> видалено з <b>${group.name}</b>.")
            },
            onFailure = { exception ->
                when (exception) {
                    is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                    is BusinessException -> CommandResponse.Error("$username не знайдено в групі.")
                    else -> CommandResponse.Error(exception.userMessage)
                }
            }
        )
    }

    suspend fun grantRole(chatId: Long, userId: Long, args: List<String>): CommandResponse {
        requireAdminAccess(chatId, userId)?.let { return it }

        if (args.size < 2) return CommandResponse.Error("Використання: /grantrole @user1,@user2 moderator|admin|member")

        val usernames = args[0].split(",").map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }
        val roleArg = args[1].uppercase()

        val role = runCatching { MemberRole.valueOf(roleArg) }.getOrNull()
            ?: return CommandResponse.Error("Невідома роль: ${args[1]}. Доступні: moderator, admin, member")

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (username in usernames) {
            memberService.getMemberByUsername(username)
                .flatMap { member -> memberService.setMemberRole(chatId, member.userId, role) }
                .fold(
                    onSuccess = { succeeded.add("@$username") },
                    onFailure = { failed.add("@$username") }
                )
        }

        val lines = mutableListOf<String>()
        if (succeeded.isNotEmpty()) lines.add("${succeeded.joinToString(", ")} отримали роль ${role.name.lowercase()}.")
        if (failed.isNotEmpty()) lines.add("Не знайдено: ${failed.joinToString(", ")}.")
        return CommandResponse.Success(lines.joinToString("\n"))
    }
}
