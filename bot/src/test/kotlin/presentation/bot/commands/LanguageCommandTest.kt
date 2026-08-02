package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.LanguageCommand
import com.ua.astrumon.presentation.controller.LanguageController
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PickerOption
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageCommandTest {
    private val languageController: LanguageController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: LanguageCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = LanguageCommand(languageController, testMessagesProvider())
    }

    private fun update(): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = "/language")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should encode the language code into each button callback`() = runTest {
        coEvery { languageController.languageOptions(chatId, userId) } returns
            PickerListing.Show(listOf(PickerOption(0L, "🇺🇦 Українська"), PickerOption(1L, "🇬🇧 English")))
        val markup = slot<InlineKeyboardMarkup>()

        command.execute(bot, update())

        verify { bot.sendMessage(ChatId.fromId(chatId), any(), ParseMode.HTML, replyMarkup = capture(markup)) }
        val callbacks = markup.captured.inlineKeyboard
            .flatten()
            .filterIsInstance<InlineKeyboardButton.CallbackData>()
            .map { it.callbackData }
        assertEquals(listOf("lang:uk", "lang:en"), callbacks)
    }

    @Test
    fun `should render the picker prompt`() = runTest {
        coEvery { languageController.languageOptions(chatId, userId) } returns
            PickerListing.Show(listOf(PickerOption(0L, "🇺🇦 Українська")))

        command.execute(bot, update())

        verify {
            bot.sendMessage(
                ChatId.fromId(chatId),
                match { it.contains("Обери мову") },
                ParseMode.HTML,
                replyMarkup = any(),
            )
        }
    }

    @Test
    fun `should reply with plain text and no keyboard when access is denied`() = runTest {
        coEvery { languageController.languageOptions(chatId, userId) } returns
            PickerListing.Reject(CommandResponse.AccessDenied("moderator"))

        command.execute(bot, update())

        verify(exactly = 1) {
            bot.sendMessage(ChatId.fromId(chatId), match { it.contains("Лише адміни та модератори") }, ParseMode.HTML)
        }
    }
}
