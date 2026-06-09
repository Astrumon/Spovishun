package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse

abstract class BaseController(
    protected val memberService: MemberService,
) {
    protected suspend fun requireModeratorAccess(
        chatId: Long,
        userId: Long,
    ): CommandResponse.AccessDenied? =
        if (!memberService.hasModeratorAccess(chatId, userId)) {
            CommandResponse.AccessDenied("moderator")
        } else {
            null
        }

    protected suspend fun requireAdminAccess(
        chatId: Long,
        userId: Long,
    ): CommandResponse.AccessDenied? =
        if (!memberService.hasAdminAccess(chatId, userId)) {
            CommandResponse.AccessDenied("admin")
        } else {
            null
        }
}
