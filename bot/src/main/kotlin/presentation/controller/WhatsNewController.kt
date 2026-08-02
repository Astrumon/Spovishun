package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.util.ReleaseNotesFormatter

class WhatsNewController(
    private val releaseNotesService: ReleaseNotesService,
    private val chatService: ChatService,
    private val memberService: MemberService,
    private val messagesProvider: BotMessagesProvider,
) {
    // These two take the bundle the caller already resolved rather than a chat id they would have
    // no other use for; it doubles as the language the notes themselves are read in.
    suspend fun showLatest(messages: BotMessages): CommandResponse = releaseNotesService.getAll(messages.language).fold(
        onSuccess = { notes ->
            val text = ReleaseNotesFormatter.formatLatest(notes)
                ?: return@fold CommandResponse.Silent
            CommandResponse.Success(text)
        },
        onFailure = { ex -> CommandResponse.Error(messages.error.prefixed(ex.userMessage)) },
    )

    suspend fun showHistory(messages: BotMessages): CommandResponse = releaseNotesService.getAll(messages.language).fold(
        onSuccess = { notes ->
            val text = ReleaseNotesFormatter.formatHistory(messages, notes)
                ?: return@fold CommandResponse.Silent
            CommandResponse.Success(text)
        },
        onFailure = { ex -> CommandResponse.Error(messages.error.prefixed(ex.userMessage)) },
    )

    suspend fun setAnnouncements(
        chatId: Long,
        userId: Long,
        enabled: Boolean,
    ): CommandResponse {
        val messages = messagesProvider.forChat(chatId)
        if (!memberService.hasAdminAccess(chatId, userId)) {
            return CommandResponse.AccessDenied("admin")
        }
        return chatService.setAnnouncementsEnabled(chatId, enabled).fold(
            onSuccess = {
                val message = if (enabled) {
                    messages.whatsNew.announcementsEnabled
                } else {
                    messages.whatsNew.announcementsDisabled
                }
                CommandResponse.Success(message)
            },
            onFailure = { ex -> CommandResponse.Error(messages.error.prefixed(ex.userMessage)) },
        )
    }
}
