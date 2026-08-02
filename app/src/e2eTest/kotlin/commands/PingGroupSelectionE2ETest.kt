package commands

import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The parts of the `/ping` group picker only Telegram can confirm: that the inline keyboard is
 * accepted and echoed back, and that a callback press produces a real, delivered message.
 *
 * Picker-listing logic and `pingGroupById` outcomes are asserted in
 * [commands.PingGroupSelectionIntegrationTest] — cheaper, and Telegram has no opinion on them.
 */
class PingGroupSelectionE2ETest : BaseE2ETest() {
    @Test
    fun `args-less ping delivers an inline keyboard Telegram accepts`() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot")
        runBlocking {
            groupService.createGroup(testChatId, "devs").getOrThrow()
            groupService.addMemberToGroup(testChatId, "devs", "helper_bot").getOrThrow()
        }

        val sent = dispatchExpectingReply("/ping")

        assertEquals(messages.ping.menuPrompt, sent.text, "Menu prompt must survive the round trip")
        val markup = assertNotNull(sent.replyMarkup, "Telegram must echo back the inline keyboard it stored")
        // One button per group plus the unconditional "all members" option.
        assertEquals(allGroups().size + 1, markup.inlineKeyboard.sumOf { it.size }, "Every option must reach Telegram")
    }

    @Test
    fun `pressing a group button delivers the ping to the chat`() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot")
        runBlocking {
            groupService.createGroup(testChatId, "callback_squad").getOrThrow()
            groupService.addMemberToGroup(testChatId, "callback_squad", "helper_bot").getOrThrow()
        }
        val group = allGroups().first { it.name == "callback_squad" }

        val handler = PingCallbackHandler(pingController, botAdminUtils, readinessSessionRunner)
        val sent = expectingReply("the ping callback") {
            runBlocking {
                handler.handle(mainBot, buildCallbackUpdate(data = "${PingCallback.PREFIX}${group.id}", messageId = 1L))
            }
        }

        assertTrue(
            sent.text?.contains("callback_squad") == true,
            "The delivered ping must name the group it was fired for",
        )
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
