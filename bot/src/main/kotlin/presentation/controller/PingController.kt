package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.GroupWithMembers
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
        args: List<String>,
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
        val icons = BotMessages.Ping.iconAll.repeat(members.size)
        val header = BotMessages.Ping.headerAll(icons, extra)
        val mentions = members.joinToString(" ") { "@${it.username}" }
        return CommandResponse.Success("$header\n\n$mentions")
    }

    suspend fun listGroupsForMenu(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
    ): ResultContainer<List<GroupWithMembers>> {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)
        return groupService.getAllGroupsWithMembers(chatId)
    }

    suspend fun pingGroupById(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        groupId: Long,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        val allGroups = groupService.getAllGroupsWithMembers(chatId)
        if (allGroups.isFailure) {
            logger.error("Failed to get groups for pingGroupById: {}", allGroups.exceptionOrNull()?.let { it::class.simpleName })
            return CommandResponse.Error(BotMessages.Error.loadGroupsInternal)
        }

        val group = allGroups.getOrNull()?.firstOrNull { it.id == groupId }
            ?: return CommandResponse.NotFound("Група", groupId.toString())

        val validMembers = mutableListOf<String>()
        for (memberUsername in group.members) {
            val memberResult = memberService.getMemberByUsername(memberUsername)
            if (memberResult.isSuccess) {
                validMembers.add(memberUsername)
            } else {
                logger.warn("Member from group id '{}' not found in member database", groupId)
            }
        }

        if (validMembers.isEmpty()) {
            return CommandResponse.Success(BotMessages.Ping.noTargets)
        }

        val icons = BotMessages.Ping.iconGroup.repeat(validMembers.size)
        val header = BotMessages.Ping.headerGroup(group.name, icons, "")
        val mentions = validMembers.joinToString(" ") { "@$it" }
        return CommandResponse.Success("$header\n\n$mentions")
    }

    suspend fun pingGroup(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        args: List<String>,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        if (args.isEmpty()) {
            return CommandResponse.Error(BotMessages.Ping.usage)
        }

        val groupKey = args[0].lowercase()
        val groupResult = groupService.getGroupByKey(chatId, groupKey)

        if (groupResult.isFailure) {
            val availableKeys = groupService
                .getAllGroupsWithMembers(chatId)
                .getOrNull()
                ?.map { it.key } ?: emptyList()
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
        val icons = BotMessages.Ping.iconGroup.repeat(validMembers.size)
        val header = BotMessages.Ping.headerGroup(group?.name ?: groupKey, icons, extra)
        val mentions = validMembers.joinToString(" ") { "@$it" }
        return CommandResponse.Success("$header\n\n$mentions")
    }
}
