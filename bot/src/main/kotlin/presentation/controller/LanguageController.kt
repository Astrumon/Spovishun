package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessagesProvider

/**
 * `/language` — reads and writes the language a chat is answered in (spovishun-152).
 *
 * Moderator-gated, like every other chat-wide setting: the language is shared by everyone in the
 * chat, so it is not a personal preference to hand to any member.
 */
class LanguageController(
    memberService: MemberService,
    private val chatService: ChatService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    /** Option ids are [BotLanguage] ordinals — [PickerOption] is keyed by id, and a language has no row. */
    suspend fun languageOptions(
        chatId: Long,
        userId: Long,
    ): PickerListing {
        requireModeratorAccess(chatId, userId)?.let { return PickerListing.Reject(it) }

        val messages = messagesProvider.forChat(chatId)
        return PickerListing.Show(
            BotLanguage.entries.map { language ->
                PickerOption(language.ordinal.toLong(), messages.languageSetting.option(language))
            },
        )
    }

    /**
     * Persists the choice and drops the cached bundle, in that order — this is the only writer, so
     * putting the invalidation here is what stops a caller from leaving the cache stale.
     *
     * The confirmation renders in the language just selected, not the one being replaced.
     * A missing chat row cannot reach the write: the moderator check above only passes for a member
     * of a registered chat, and registering a member is what creates the chat row.
     */
    suspend fun setLanguage(
        chatId: Long,
        userId: Long,
        language: BotLanguage,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        return chatService.setLanguage(chatId, language).fold(
            onSuccess = {
                messagesProvider.invalidate(chatId)
                CommandResponse.Success(messagesProvider.forLanguage(language).languageSetting.changed(language))
            },
            onFailure = { CommandResponse.Error(it.userMessage) },
        )
    }
}
