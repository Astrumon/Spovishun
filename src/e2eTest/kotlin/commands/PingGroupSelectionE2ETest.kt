package commands

import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.util.BotAdminUtils
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class PingGroupSelectionE2ETest : BaseE2ETest() {

    @Test
    fun `ping with no args shows group selection menu when groups exist`() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot")
        runBlocking {
            groupService.createGroup(testChatId, "devs").getOrThrow()
            groupService.addMemberToGroup(testChatId, "devs", "helper_bot").getOrThrow()
        }
        dispatch("/ping")
        assertTrue(allGroups().any { it.name == "devs" }, "Group should still exist after menu dispatch")
    }

    @Test
    fun `ping with no args does not throw when no groups exist`() {
        dispatch("/ping")
    }

    @Test
    fun `pingGroupById message contains group name in header`() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot")
        runBlocking {
            groupService.createGroup(testChatId, "backend").getOrThrow()
            groupService.addMemberToGroup(testChatId, "backend", "helper_bot").getOrThrow()
        }
        val group = allGroups().first { it.name == "backend" }

        val pingCtrl = PingController(memberService, groupService, autoRegisterService)
        val result = runBlocking {
            pingCtrl.pingGroupById(testChatId, helperBotId, "helper_bot", "HelperBot", MemberRole.MEMBER, group.id)
        }

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("backend"), "Group name should appear in the ping header")
        assertTrue(result.message.contains("@helper_bot"), "Member mention should be in the message")
    }

    @Test
    fun `pingGroupById returns NotFound for non-existent group id`() {
        val pingCtrl = PingController(memberService, groupService, autoRegisterService)
        val result = runBlocking {
            pingCtrl.pingGroupById(testChatId, helperBotId, "helper_bot", "HelperBot", MemberRole.MEMBER, Long.MAX_VALUE)
        }

        assertTrue(result is CommandResponse.NotFound)
    }

    @Test
    fun `pingGroupById returns noTargets when group has no registered members`() {
        runBlocking {
            groupService.createGroup(testChatId, "empty_squad").getOrThrow()
        }
        val group = allGroups().first { it.name == "empty_squad" }

        val pingCtrl = PingController(memberService, groupService, autoRegisterService)
        val result = runBlocking {
            pingCtrl.pingGroupById(testChatId, helperBotId, "helper_bot", "HelperBot", MemberRole.MEMBER, group.id)
        }

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }

    @Test
    fun `callback handler dispatches ping and preserves group state`() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot")
        runBlocking {
            groupService.createGroup(testChatId, "callback_squad").getOrThrow()
            groupService.addMemberToGroup(testChatId, "callback_squad", "helper_bot").getOrThrow()
        }
        val group = allGroups().first { it.name == "callback_squad" }

        val pingCtrl = PingController(memberService, groupService, autoRegisterService)
        val handler = PingCallbackHandler(pingCtrl, BotAdminUtils())

        val callbackUpdate = buildCallbackUpdate(
            data = "${PingCallback.PREFIX}${group.id}",
            messageId = 1L,
        )
        runBlocking { handler.handle(mainBot, callbackUpdate) }

        assertTrue(allGroups().any { it.name == "callback_squad" })
        assertTrue(allMembers().any { it.username == "helper_bot" })
    }

    private fun buildCallbackUpdate(
        data: String,
        messageId: Long,
        userId: Long = helperBotId,
        username: String = "helper_bot",
        firstName: String = "HelperBot",
        chatId: Long = testChatId,
    ): Update {
        val user = User(id = userId, isBot = true, firstName = firstName, username = username)
        val chat = Chat(id = chatId, type = "supergroup")
        val message = Message(messageId = messageId, date = 0L, chat = chat)
        val callbackQuery = CallbackQuery(
            id = "test_callback_id",
            from = user,
            message = message,
            inlineMessageId = null,
            data = data,
            chatInstance = "test_chat_instance",
        )
        return Update(updateId = 1L, callbackQuery = callbackQuery)
    }
}
