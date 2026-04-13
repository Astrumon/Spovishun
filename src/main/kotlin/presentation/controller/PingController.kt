package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import org.slf4j.LoggerFactory

class PingController(
    private val memberService: MemberService,
    private val groupService: GroupService,
    private val autoRegisterService: AutoRegisterService,
) {
    private val logger = LoggerFactory.getLogger(PingController::class.java)

    suspend fun pingAll(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        args: List<String>
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        val membersResult = memberService.getAllMembersInChat(chatId)
        if (membersResult.isFailure) {
            logger.error("Failed to get all members for pingAll: {}", membersResult.exceptionOrNull()?.message)
            return CommandResponse.Error("Failed to load members")
        }

        val members = membersResult.getOrNull() ?: emptyList()
        if (members.isEmpty()) {
            return CommandResponse.Success("Немає зареєстрованих учасників.")
        }

        val extra = args.joinToString(" ")
        val crabs = "🗿".repeat(members.size)
        val header = if (extra.isNotEmpty()) "📢 $crabs $extra" else "📢 $crabs"
        val mentions = members.joinToString(" ") { "@${it.username}" }
        return CommandResponse.Success("$header\n\n$mentions")
    }

    suspend fun pingGroup(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        args: List<String>
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        if (args.isEmpty()) {
            return CommandResponse.Error("Використання: /ping &lt;група&gt; [текст]")
        }

        val groupKey = args[0].lowercase()
        val groupResult = groupService.getGroupByKey(chatId, groupKey)

        if (groupResult.isFailure) {
            val availableKeys = groupService.getAllGroupsWithMembers(chatId)
                .getOrNull()?.map { it.key } ?: emptyList()
            return CommandResponse.NotFound("Група", groupKey, availableKeys)
        }

        val group = groupResult.getOrNull()
        val validMembers = mutableListOf<String>()
        if (group?.members?.isNotEmpty() == true) {
            for (memberUsername in group.members) {
                val memberResult = memberService.getMemberByUsername(memberUsername)
                if (memberResult.isSuccess) {
                    validMembers.add(memberUsername)
                } else {
                    logger.warn("Member '{}' from group '{}' not found in member database", memberUsername, groupKey)
                }
            }
        }

        if (validMembers.isEmpty()) {
            return CommandResponse.Success("Немає кого пінгувати.")
        }

        val extra = args.drop(1).joinToString(" ")
        val crabs = "🦞".repeat(validMembers.size)
        val header = if (extra.isNotEmpty()) "📣 $crabs $extra" else "📣 $crabs"
        val mentions = validMembers.joinToString(" ") { "@$it" }
        return CommandResponse.Success("$header\n\n$mentions")
    }
}
