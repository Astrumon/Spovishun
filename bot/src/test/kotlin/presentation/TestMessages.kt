package presentation

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import io.mockk.coEvery
import io.mockk.mockk

/** The Ukrainian bundle — what every pre-existing assertion was written against. */
internal val ukMessages = BotMessages.of(BotLanguage.UK)

/**
 * A real [BotMessagesProvider] over a stubbed [ChatService], so tests exercise the actual bundle
 * lookup and caching rather than a hand-rolled double.
 */
internal fun testMessagesProvider(language: BotLanguage = BotLanguage.UK): BotMessagesProvider = BotMessagesProvider(
    mockk<ChatService> {
        coEvery { getLanguage(any()) } returns ResultContainer.success(language)
    },
)
