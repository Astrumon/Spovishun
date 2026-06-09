package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.RandomCommand
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RandomCommandTest {
    private val randomController: RandomController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: RandomCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = RandomCommand(randomController, botAdminUtils)
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun buildUpdate(text: String): Update {
        val chat = Chat(id = chatId, type = "supergroup")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = user, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should call pickRandomAll when no args given`() =
        runTest {
            val update = buildUpdate("/random")
            coEvery { randomController.pickRandomAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER) } returns
                CommandResponse.Success("🎲 Випало: @alice")

            command.execute(bot, update)

            coVerify { randomController.pickRandomAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER) }
            coVerify(exactly = 0) { randomController.pickRandomFromGroup(any(), any(), any(), any(), any(), any()) }
            coVerify { bot.sendMessage(ChatId.fromId(chatId), "🎲 Випало: @alice", ParseMode.HTML) }
        }

    @Test
    fun `should call pickRandomFromGroup with lowercase key when group arg given`() =
        runTest {
            val update = buildUpdate("/random Devs")
            coEvery { randomController.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "devs") } returns
                CommandResponse.Success("🎲 Випало: @bob")

            command.execute(bot, update)

            coVerify { randomController.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "devs") }
            coVerify(exactly = 0) { randomController.pickRandomAll(any(), any(), any(), any(), any()) }
            coVerify { bot.sendMessage(ChatId.fromId(chatId), "🎲 Випало: @bob", ParseMode.HTML) }
        }

    @Test
    fun `should send group not found HTML when group does not exist`() =
        runTest {
            val update = buildUpdate("/random ghost")
            coEvery { randomController.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "ghost") } returns
                CommandResponse.NotFound("Група", "ghost", listOf("devs", "qa"))

            command.execute(bot, update)

            coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("не знайдено") && it.contains("devs") }, ParseMode.HTML) }
        }

    @Test
    fun `should return early when message is null`() =
        runTest {
            val update = Update(updateId = 1L, message = null)

            command.execute(bot, update)

            coVerify(exactly = 0) { randomController.pickRandomAll(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { randomController.pickRandomFromGroup(any(), any(), any(), any(), any(), any()) }
        }
}
