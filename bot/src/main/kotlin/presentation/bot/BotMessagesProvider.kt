package com.ua.astrumon.presentation.bot

import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.service.ChatService
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the [BotMessages] a chat should be answered in.
 *
 * Every command, controller and handler goes through here instead of a singleton, so a chat's
 * language is a lookup rather than a compile-time constant. The per-chat map is what keeps the
 * language effectively "resolved once per update": the first caller in an update pays a DB read,
 * everyone downstream gets a map hit.
 *
 * [invalidate] must be called whenever the stored language changes — [LanguageController] owns
 * that, being the only writer.
 */
class BotMessagesProvider(
    private val chatService: ChatService,
) {
    private val logger = LoggerFactory.getLogger(BotMessagesProvider::class.java)

    private val byChat = ConcurrentHashMap<Long, BotMessages>()

    suspend fun forChat(chatId: Long): BotMessages = byChat[chatId] ?: forLanguage(resolveLanguage(chatId)).also { byChat[chatId] = it }

    fun forLanguage(language: BotLanguage): BotMessages = BotMessages.of(language)

    fun invalidate(chatId: Long) {
        byChat.remove(chatId)
    }

    /**
     * A failed read falls back to Ukrainian rather than propagating: a database hiccup must not cost
     * the user their reply. It is logged so the fallback never passes silently.
     */
    private suspend fun resolveLanguage(chatId: Long): BotLanguage = chatService.getLanguage(chatId).fold(
        onSuccess = { it },
        onFailure = {
            logger.warn("Falling back to {} — could not read chat language: {}", BotLanguage.UK.code, it.message)
            BotLanguage.UK
        },
    )
}
