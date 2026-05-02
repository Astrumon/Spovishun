package presentation.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.TelegramBot
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramBotIdentityTest {

    private val commandRegistry: CommandRegistry = mockk(relaxed = true)
    private val messageHandler: MessageHandler = mockk(relaxed = true)
    private val config: AppConfig = mockk(relaxed = true)
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var telegramBot: TelegramBot

    @BeforeTest
    fun setup() {
        clearAllMocks()
        telegramBot = TelegramBot(commandRegistry, messageHandler, config)
    }

    @Test
    fun `verifyIdentity should return true when expectedUsername is null`() {
        assertTrue(telegramBot.verifyIdentity(bot, null))
    }

    @Test
    fun `verifyIdentity should return true when expectedUsername is blank`() {
        assertTrue(telegramBot.verifyIdentity(bot, "   "))
    }

    @Test
    fun `verifyIdentity should return true when bot username matches expected`() {
        val botUser = User(id = 1L, isBot = true, firstName = "MyBot", username = "my_bot")
        every { bot.getMe() } returns TelegramBotResult.Success(botUser)

        assertTrue(telegramBot.verifyIdentity(bot, "my_bot"))
    }

    @Test
    fun `verifyIdentity should return false when bot username does not match expected`() {
        val botUser = User(id = 1L, isBot = true, firstName = "MyBot", username = "actual_bot")
        every { bot.getMe() } returns TelegramBotResult.Success(botUser)

        assertFalse(telegramBot.verifyIdentity(bot, "expected_bot"))
    }

    @Test
    fun `verifyIdentity should return false when getMe fails`() {
        every { bot.getMe() } returns TelegramBotResult.Error.Unknown(Exception("network error"))

        assertFalse(telegramBot.verifyIdentity(bot, "my_bot"))
    }

    @Test
    fun `verifyIdentity should return false when bot has no username`() {
        val botUser = User(id = 1L, isBot = true, firstName = "MyBot", username = null)
        every { bot.getMe() } returns TelegramBotResult.Success(botUser)

        assertFalse(telegramBot.verifyIdentity(bot, "my_bot"))
    }
}
