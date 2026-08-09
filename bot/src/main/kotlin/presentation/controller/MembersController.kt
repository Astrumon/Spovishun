package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.badge
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessagesProvider

class MembersController(
    memberService: MemberService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    suspend fun getMembers(chatId: Long): CommandResponse {
        val messages = messagesProvider.forChat(chatId)
        return memberService.getAllMembersInChat(chatId).fold(
            onSuccess = { members ->
                if (members.isEmpty()) {
                    CommandResponse.Success(messages.member.empty)
                } else {
                    val lines = mutableListOf(messages.member.listHeader)
                    members.forEach { m ->
                        val display = if (m.username.startsWith("user_")) {
                            m.firstName.escapeHtml()
                        } else {
                            "@${m.username.escapeHtml()}${m.role.badge()}"
                        }
                        lines.add(messages.member.listItem(display))
                    }
                    CommandResponse.Success(lines.joinToString("\n") + messages.member.totalSuffix(members.size))
                }
            },
            onFailure = { CommandResponse.Error(it.userMessage) },
        )
    }
}
