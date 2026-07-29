package commands

import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.controller.PingController
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PingCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `pingAll with no registered members should send empty list message`() = runTest {
        val update = buildUpdate("/all")

        pingAllCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                any<String>(),
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `pingAll with registered members should mention all of them`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        registerMember(userId = 3L, username = "carol")
        val update = buildUpdate("/all")

        pingAllCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") && it.contains("@bob") && it.contains("@carol") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `pingAll with trailing text should include it in message`() = runTest {
        registerMember(userId = 1L, username = "alice")
        val update = buildUpdate("/all standup time")

        pingAllCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("standup time") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `pingGroup with valid group should mention group members`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")
        groupService.addMemberToGroup(testChatId, "devs", "bob")
        val update = buildUpdate("/ping devs")

        pingGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") && it.contains("@bob") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `pingGroup with unknown group key should show available groups`() = runTest {
        registerMember()
        groupService.createGroup(testChatId, "devs")
        val update = buildUpdate("/ping unknown")

        pingGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("devs") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `ping menu all option should mention every registered member`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")

        pingCallbackHandler().handle(bot, buildCallbackUpdate(PingController.ALL_MEMBERS_ID))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") && it.contains("@bob") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `ping menu group option should mention only that group's members`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")
        val devs = groupService.getAllGroupsWithMembers(testChatId).getOrThrow().first { it.key == "devs" }

        pingCallbackHandler().handle(bot, buildCallbackUpdate(devs.id))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") && !it.contains("@bob") },
                ParseMode.HTML,
            )
        }
    }

    private fun pingCallbackHandler() = PingCallbackHandler(pingController, botAdminUtils)

    private fun buildCallbackUpdate(optionId: Long): Update {
        val user = User(id = testUserId, isBot = false, firstName = testFirstName, username = testUsername)
        val chat = Chat(id = testChatId, type = "supergroup")
        val callbackQuery = CallbackQuery(
            id = "test_callback_id",
            from = user,
            message = Message(messageId = 1L, date = 0L, chat = chat),
            inlineMessageId = null,
            data = "${PingCallback.PREFIX}$optionId",
            chatInstance = "test_chat_instance",
        )
        return Update(updateId = 1L, callbackQuery = callbackQuery)
    }
}
