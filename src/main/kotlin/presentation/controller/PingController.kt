package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
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
            logger.error("Failed to get all members for pingAll: {}", membersResult.exceptionOrNull()?.let { it::class.simpleName })
            return CommandResponse.Error(BotMessages.Error.loadMembersInternal)
        }

        val members = membersResult.getOrNull() ?: emptyList()
        if (members.isEmpty()) {
            return CommandResponse.Success(BotMessages.Ping.noRegistered)
        }

        val extra = args.joinToString(" ")
        val crabs = BotMessages.Ping.crabAll.repeat(members.size)
        val header = BotMessages.Ping.headerAll(crabs, extra)
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
            return CommandResponse.Error(BotMessages.Ping.usage)
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
                    logger.warn("Member from group '{}' not found in member database", groupKey)
                }
            }
        }

        if (validMembers.isEmpty()) {
            return CommandResponse.Success(BotMessages.Ping.noTargets)
        }

        val extra = args.drop(1).joinToString(" ")
        val crabs = BotMessages.Ping.crabGroup.repeat(validMembers.size)
        val header = BotMessages.Ping.headerGroup(crabs, extra)
        val mentions = validMembers.joinToString(" ") { "@$it" }
        return CommandResponse.Success("$header\n\n$mentions")
    }
}
