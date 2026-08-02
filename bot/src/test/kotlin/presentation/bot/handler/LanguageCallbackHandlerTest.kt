package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.LanguageCallbackHandler
import com.ua.astrumon.presentation.controller.LanguageController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test

class LanguageCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val languageController: LanguageController = mockk()
    private lateinit var handler: LanguageCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = LanguageCallbackHandler(languageController, testMessagesProvider())
    }

    private fun update(data: String): Update = callbackUpdate(chatId, clickerId, data)

    @Test
    fun `should persist the language decoded from the payload`() = runTest {
        coEvery { languageController.setLanguage(chatId, clickerId, BotLanguage.EN) } returns
            CommandResponse.Success("Chat language changed")

        handler.handle(bot, update("lang:en"))

        coVerify(exactly = 1) { languageController.setLanguage(chatId, clickerId, BotLanguage.EN) }
    }

    @Test
    fun `should fall back to Ukrainian for an unknown code`() = runTest {
        coEvery { languageController.setLanguage(chatId, clickerId, BotLanguage.UK) } returns
            CommandResponse.Success("Мову змінено")

        handler.handle(bot, update("lang:de"))

        coVerify(exactly = 1) { languageController.setLanguage(chatId, clickerId, BotLanguage.UK) }
    }

    @Test
    fun `should acknowledge the callback query`() = runTest {
        coEvery { languageController.setLanguage(any(), any(), any()) } returns CommandResponse.Success("ok")

        handler.handle(bot, update("lang:uk"))

        verify(exactly = 1) { bot.answerCallbackQuery(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should close the picker with the confirmation text`() = runTest {
        coEvery { languageController.setLanguage(chatId, clickerId, BotLanguage.EN) } returns
            CommandResponse.Success("Chat language changed")

        handler.handle(bot, update("lang:en"))

        verify(exactly = 1) { bot.deleteMessage(ChatId.fromId(chatId), any()) }
        verify(exactly = 1) {
            bot.sendMessage(ChatId.fromId(chatId), match { it.contains("Chat language changed") }, ParseMode.HTML)
        }
    }

    @Test
    fun `should render an access denial instead of the confirmation`() = runTest {
        coEvery { languageController.setLanguage(chatId, clickerId, BotLanguage.EN) } returns
            CommandResponse.AccessDenied("moderator")

        handler.handle(bot, update("lang:en"))

        verify(exactly = 1) {
            bot.sendMessage(ChatId.fromId(chatId), match { it.contains("Лише адміни та модератори") }, ParseMode.HTML)
        }
    }
}
