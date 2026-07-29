package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
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

    /**
     * Options for the args-less `/ping` picker: the whole-chat option first, then one per group.
     * The whole-chat option is always present, so the menu still renders in a chat with no groups.
     */
    suspend fun groupsForPicker(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
    ): PickerListing {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        return groupService.getAllGroupsWithMembers(chatId).fold(
            onSuccess = { groups ->
                val allMembers = PickerOption(ALL_MEMBERS_ID, BotMessages.Ping.allMembersOption)
                PickerListing.Show(listOf(allMembers) + groups.map { PickerOption(it.id, it.name) })
            },
            onFailure = {
                logger.error("Failed to get groups for groupsForPicker: {}", it::class.simpleName)
                PickerListing.Reject(CommandResponse.Error(BotMessages.Error.loadGroupsInternal))
            },
        )
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

    companion object {
        /** Sentinel picker-option id for "the whole chat" — group ids are DB serials and start at 1. */
        const val ALL_MEMBERS_ID = 0L
    }
}
