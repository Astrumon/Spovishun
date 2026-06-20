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
}
