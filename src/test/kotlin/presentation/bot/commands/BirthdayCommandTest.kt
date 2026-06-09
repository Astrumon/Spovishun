package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.BirthdayCommand
import com.ua.astrumon.presentation.controller.BirthdayController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class BirthdayCommandTest {
    private val birthdayController: BirthdayController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: BirthdayCommand

    private val chatId = -100L
    private val userId = 456L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = BirthdayCommand(birthdayController)
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()
    }

    private fun createUpdate(text: String): Update {
        val chat = Chat(id = chatId, type = "group")
        val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `execute should send usage message when args are empty`() =
        runTest {
            val update = createUpdate("/birthday")

            command.execute(bot, update)

            coVerify(exactly = 0) { birthdayController.setOwnBirthday(any(), any()) }
            coVerify(exactly = 0) { birthdayController.clearOwnBirthday(any()) }
            coVerify(exactly = 0) { birthdayController.setBirthdayForOther(any(), any(), any()) }
            coVerify { bot.sendMessage(ChatId.fromId(chatId), any(), ParseMode.HTML) }
        }

    @Test
    fun `execute should call clearOwnBirthday when arg is off`() =
        runTest {
            coEvery { birthdayController.clearOwnBirthday(userId) } returns CommandResponse.Success("cleared")
            val update = createUpdate("/birthday off")

            command.execute(bot, update)

            coVerify { birthdayController.clearOwnBirthday(userId) }
            coVerify(exactly = 0) { birthdayController.setOwnBirthday(any(), any()) }
        }

    @Test
    fun `execute should call clearOwnBirthday case-insensitively`() =
        runTest {
            coEvery { birthdayController.clearOwnBirthday(userId) } returns CommandResponse.Success("cleared")
            val update = createUpdate("/birthday OFF")

            command.execute(bot, update)

            coVerify { birthdayController.clearOwnBirthday(userId) }
        }

    @Test
    fun `execute should call setOwnBirthday when single date arg`() =
        runTest {
            coEvery { birthdayController.setOwnBirthday(userId, "25.12") } returns CommandResponse.Success("saved")
            val update = createUpdate("/birthday 25.12")

            command.execute(bot, update)

            coVerify { birthdayController.setOwnBirthday(userId, "25.12") }
            coVerify(exactly = 0) { birthdayController.setBirthdayForOther(any(), any(), any()) }
        }

    @Test
    fun `execute should call setBirthdayForOther when date and username args provided`() =
        runTest {
            coEvery { birthdayController.setBirthdayForOther(chatId, userId, listOf("25.12", "@bob")) } returns
                CommandResponse.Success("saved")
            val update = createUpdate("/birthday 25.12 @bob")

            command.execute(bot, update)

            coVerify { birthdayController.setBirthdayForOther(chatId, userId, listOf("25.12", "@bob")) }
            coVerify(exactly = 0) { birthdayController.setOwnBirthday(any(), any()) }
        }

    @Test
    fun `execute should return early when message is null`() =
        runTest {
            val update = Update(updateId = 1L, message = null)

            command.execute(bot, update)

            coVerify(exactly = 0) { birthdayController.setOwnBirthday(any(), any()) }
            coVerify(exactly = 0) { birthdayController.clearOwnBirthday(any()) }
            coVerify(exactly = 0) { birthdayController.setBirthdayForOther(any(), any(), any()) }
            coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        }

    @Test
    fun `execute should send reply after controller response`() =
        runTest {
            coEvery { birthdayController.setOwnBirthday(userId, "01.01") } returns
                CommandResponse.Success("День народження збережено: <b>01.01</b>.")
            val update = createUpdate("/birthday 01.01")

            command.execute(bot, update)

            coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("01.01") }, ParseMode.HTML) }
        }
}
