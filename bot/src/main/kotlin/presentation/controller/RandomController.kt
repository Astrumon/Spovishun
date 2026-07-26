package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import org.slf4j.LoggerFactory

class RandomController(
    private val memberService: MemberService,
    private val groupService: GroupService,
    private val autoRegisterService: AutoRegisterService,
) {
    private val logger = LoggerFactory.getLogger(RandomController::class.java)

    suspend fun pickRandomAll(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        val membersResult = memberService.getAllMembersInChat(chatId)
        if (membersResult.isFailure) {
            logger.error("Failed to get members for pickRandomAll: {}", membersResult.exceptionOrNull()?.let { it::class.simpleName })
            return CommandResponse.Error(BotMessages.Error.loadMembersInternal)
        }

        val members = membersResult.getOrNull() ?: emptyList()
        if (members.isEmpty()) return CommandResponse.Success(BotMessages.Random.noRegistered)

        val picked = members.random().username
        return CommandResponse.Success(BotMessages.Random.result(picked))
    }

    suspend fun pickRandomFromGroup(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        key: String,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        val groupResult = groupService.getGroupByKey(chatId, key)
        if (groupResult.isFailure) {
            val availableKeys = groupService
                .getAllGroupsWithMembers(chatId)
                .getOrNull()
                ?.map { it.key } ?: emptyList()
            return CommandResponse.NotFound("Група", key, availableKeys)
        }

        val members = groupResult.getOrNull()?.members ?: emptyList()
        if (members.isEmpty()) return CommandResponse.Success(BotMessages.Random.emptyGroup)

        val picked = members.random()
        return CommandResponse.Success(BotMessages.Random.result(picked))
    }

    /**
     * Options for the args-less `/random` picker: the whole-chat option first, then one per group.
     * An empty [PickerListing.Show] means the chat has no groups — the caller falls back to [pickRandomAll].
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
                if (groups.isEmpty()) {
                    PickerListing.Show(emptyList())
                } else {
                    val allMembers = PickerOption(ALL_MEMBERS_ID, BotMessages.Random.allMembersOption)
                    PickerListing.Show(listOf(allMembers) + groups.map { PickerOption(it.id, it.name) })
                }
            },
            onFailure = {
                logger.error("Failed to get groups for groupsForPicker: {}", it::class.simpleName)
                PickerListing.Reject(CommandResponse.Error(BotMessages.Error.loadGroupsInternal))
            },
        )
    }

    suspend fun pickRandomFromGroupById(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        groupId: Long,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        val groupsResult = groupService.getAllGroupsWithMembers(chatId)
        if (groupsResult.isFailure) {
            logger.error(
                "Failed to get groups for pickRandomFromGroupById: {}",
                groupsResult.exceptionOrNull()?.let { it::class.simpleName },
            )
            return CommandResponse.Error(BotMessages.Error.loadGroupsInternal)
        }

        val group = groupsResult.getOrNull()?.firstOrNull { it.id == groupId }
            ?: return CommandResponse.NotFound("Група", groupId.toString())

        if (group.members.isEmpty()) return CommandResponse.Success(BotMessages.Random.emptyGroup)

        val picked = group.members.random()
        return CommandResponse.Success(BotMessages.Random.result(picked))
    }

    companion object {
        /** Sentinel picker-option id for "the whole chat" — group ids are DB serials and start at 1. */
        const val ALL_MEMBERS_ID = 0L
    }
}
