package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.badge
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages

class MembersController(
    memberService: MemberService,
    private val autoRegisterService: AutoRegisterService,
) : BaseController(memberService) {
    suspend fun getMembers(
        chatId: Long,
        member: Member,
        userRole: MemberRole,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = member.userId,
            username = member.username,
            firstName = member.firstName,
            userRole = userRole,
        )

        return memberService.getAllMembersInChat(chatId).fold(
            onSuccess = { members ->
                if (members.isEmpty()) {
                    CommandResponse.Success(BotMessages.Member.empty)
                } else {
                    val lines = mutableListOf(BotMessages.Member.listHeader)
                    members.forEach { m ->
                        val display = if (m.username.startsWith("user_")) {
                            m.firstName.escapeHtml()
                        } else {
                            "@${m.username.escapeHtml()}${m.role.badge()}"
                        }
                        lines.add(BotMessages.Member.listItem(display))
                    }
                    CommandResponse.Success(lines.joinToString("\n") + BotMessages.Member.totalSuffix(members.size))
                }
            },
            onFailure = { CommandResponse.Error(it.userMessage) },
        )
    }
}
