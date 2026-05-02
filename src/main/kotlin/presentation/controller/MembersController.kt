package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.badge
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.MemberService

class MembersController(
    memberService: MemberService,
    private val autoRegisterService: AutoRegisterService,
) : BaseController(memberService) {

    suspend fun getMembers(chatId: Long, member: Member, userRole: MemberRole): CommandResponse {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = member.userId,
            username = member.username,
            firstName = member.firstName,
            userRole = userRole
        )

        return memberService.getAllMembersInChat(chatId).fold(
            onSuccess = { members ->
                if (members.isEmpty()) {
                    CommandResponse.Success("📋 <b>Немає зареєстрованих учасників</b>.\n\nНапиши будь-яке повідомлення, щоб зареєструватися!")
                } else {
                    val lines = mutableListOf("📋 <b>Зареєстровані учасники:</b>")
                    members.forEach { m ->
                        val display = if (m.username.startsWith("user_")) {
                            m.firstName.escapeHtml()
                        } else {
                            "@${m.username.escapeHtml()}${m.role.badge()}"
                        }
                        lines.add("• $display")
                    }
                    CommandResponse.Success(lines.joinToString("\n") + "\n\n📝 Всього: ${members.size} учасників")
                }
            },
            onFailure = { CommandResponse.Error(it.userMessage) }
        )
    }
}
