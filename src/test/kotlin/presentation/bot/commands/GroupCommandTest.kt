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
import com.ua.astrumon.presentation.bot.commands.GroupCommand
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class GroupCommandTest {

    private val groupController: GroupController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var groupCommand: GroupCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        groupCommand = GroupCommand(groupController, botAdminUtils)
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun createUpdate(
        fromUser: User? = user,
        chatIdVal: Long = chatId,
        text: String = "/groups"
    ): Update {
        val chat = Chat(id = chatIdVal, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    // --- showGroups ---

    @Test
    fun `showGroups should call controller and send message body`() = runTest {
        val update = createUpdate()
        coEvery { groupController.getGroups(chatId, any(), MemberRole.MEMBER) } returns CommandResponse.Success("groups list")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        groupCommand.showGroups(bot, update)

        coVerify { groupController.getGroups(chatId, any(), MemberRole.MEMBER) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "groups list", ParseMode.HTML) }
    }

    @Test
    fun `showGroups should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        groupCommand.showGroups(bot, update)

        coVerify(exactly = 0) { groupController.getGroups(any(), any(), any()) }
    }

    // --- addNewGroup ---

    @Test
    fun `addNewGroup should pass args to controller and prefix success with checkmark`() = runTest {
        val update = createUpdate(text = "/newgroup devs")
        coEvery { groupController.createGroup(chatId, userId, listOf("devs")) } returns CommandResponse.Success("Група <b>devs</b> створена!")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        groupCommand.addNewGroup(bot, update)

        coVerify { groupController.createGroup(chatId, userId, listOf("devs")) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.startsWith("✅") }, ParseMode.HTML) }
    }

    @Test
    fun `addNewGroup should send access denied message when access denied`() = runTest {
        val update = createUpdate(text = "/newgroup devs")
        coEvery { groupController.createGroup(chatId, userId, listOf("devs")) } returns CommandResponse.AccessDenied("moderator")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        groupCommand.addNewGroup(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("🚫") }, ParseMode.HTML) }
    }

    @Test
    fun `addNewGroup should pass empty args when no arguments`() = runTest {
        val update = createUpdate(text = "/newgroup")
        coEvery { groupController.createGroup(chatId, userId, emptyList()) } returns CommandResponse.Error("usage")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        groupCommand.addNewGroup(bot, update)

        coVerify { groupController.createGroup(chatId, userId, emptyList()) }
    }

    @Test
    fun `addNewGroup should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        groupCommand.addNewGroup(bot, update)

        coVerify(exactly = 0) { groupController.createGroup(any(), any(), any()) }
    }

    // --- deleteGroup ---

    @Test
    fun `deleteGroup should pass args to controller and prefix success`() = runTest {
        val update = createUpdate(text = "/delgroup devs")
        coEvery { groupController.deleteGroup(chatId, userId, listOf("devs")) } returns CommandResponse.Success("Група <b>devs</b> видалена.")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        groupCommand.deleteGroup(bot, update)

        coVerify { groupController.deleteGroup(chatId, userId, listOf("devs")) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.startsWith("🗑") }, ParseMode.HTML) }
    }

    @Test
    fun `deleteGroup should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        groupCommand.deleteGroup(bot, update)

        coVerify(exactly = 0) { groupController.deleteGroup(any(), any(), any()) }
    }

    // --- addUserToGroup ---

    @Test
    fun `addUserToGroup should pass args to controller`() = runTest {
        val update = createUpdate(text = "/addtogroup devs @bob")
        coEvery { groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob")) } returns CommandResponse.Success("bob додано до devs")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        groupCommand.addUserToGroup(bot, update)

        coVerify { groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob")) }
    }

    @Test
    fun `addUserToGroup should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        groupCommand.addUserToGroup(bot, update)

        coVerify(exactly = 0) { groupController.addUserToGroup(any(), any(), any()) }
    }

    // --- removeUserFromGroup ---

    @Test
    fun `removeUserFromGroup should pass args to controller`() = runTest {
        val update = createUpdate(text = "/removefromgroup devs @bob")
        coEvery { groupController.removeUserFromGroup(chatId, userId, listOf("devs", "@bob")) } returns CommandResponse.Success("bob видалено з devs")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        groupCommand.removeUserFromGroup(bot, update)

        coVerify { groupController.removeUserFromGroup(chatId, userId, listOf("devs", "@bob")) }
    }

    @Test
    fun `removeUserFromGroup should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        groupCommand.removeUserFromGroup(bot, update)

        coVerify(exactly = 0) { groupController.removeUserFromGroup(any(), any(), any()) }
    }
}
