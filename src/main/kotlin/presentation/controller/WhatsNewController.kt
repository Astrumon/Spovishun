package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.service.ChatService
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.domain.service.ReleaseNotesService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.util.ReleaseNotesFormatter

class WhatsNewController(
    private val releaseNotesService: ReleaseNotesService,
    private val chatService: ChatService,
    private val memberService: MemberService,
) {

    suspend fun showLatest(): CommandResponse =
        releaseNotesService.getAll().fold(
            onSuccess = { notes ->
                val text = ReleaseNotesFormatter.formatLatest(notes)
                    ?: return@fold CommandResponse.Silent
                CommandResponse.Success(text)
            },
            onFailure = { ex -> CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage)) }
        )

    suspend fun showHistory(): CommandResponse =
        releaseNotesService.getAll().fold(
            onSuccess = { notes ->
                if (notes.isEmpty()) return@fold CommandResponse.Silent
                CommandResponse.Success(ReleaseNotesFormatter.formatHistory(notes))
            },
            onFailure = { ex -> CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage)) }
        )

    suspend fun setAnnouncements(chatId: Long, userId: Long, enabled: Boolean): CommandResponse {
        if (!memberService.hasAdminAccess(chatId, userId)) {
            return CommandResponse.AccessDenied("admin")
        }
        return chatService.setAnnouncementsEnabled(chatId, enabled).fold(
            onSuccess = {
                val message = if (enabled) BotMessages.WhatsNew.announcementsEnabled
                else BotMessages.WhatsNew.announcementsDisabled
                CommandResponse.Success(message)
            },
            onFailure = { ex -> CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage)) }
        )
    }
}
