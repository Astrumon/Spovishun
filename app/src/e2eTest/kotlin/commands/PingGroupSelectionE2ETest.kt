package commands

import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.util.BotAdminUtils
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertMenuOffersAllOptionFirst()
    }

    /**
     * The `All` option is prepended unconditionally, so a chat with zero groups still gets a menu
     * instead of the old "no groups" text — otherwise the default option would be unreachable there.
     */
    @Test
    fun `ping with no args still offers the all-members option when no groups exist`() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot")

        dispatch("/ping")

        assertEquals(0, allGroups().size, "Precondition: the chat must have no groups")
        assertMenuOffersAllOptionFirst()
    }

    /** Asserts the args-less `/ping` menu opens with the whole-chat option, followed by one entry per group. */
    private fun assertMenuOffersAllOptionFirst() {
        val pingCtrl = PingController(memberService, groupService, autoRegisterService)
        val listing = runBlocking {
            pingCtrl.groupsForPicker(testChatId, helperBotId, "helper_bot", "HelperBot", MemberRole.MEMBER)
        }

        assertTrue(listing is PickerListing.Show, "Menu must render, not fall back to text")
        assertEquals(PingController.ALL_MEMBERS_ID, listing.options.first().id, "All option must come first")
        assertEquals(allGroups().size + 1, listing.options.size, "Menu must hold the All option plus every group")
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
            chatService.ensureChat(testChatId, null, null).getOrThrow()
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
