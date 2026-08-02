package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.controller.PingController
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PingCommandIntegrationTest : BaseIntegrationTest() {
    /** `/all` auto-registers its caller first, so the roster is never empty — it holds just them. */
    @Test
    fun `pingAll from an unregistered caller should poll that caller alone`() = runTest {
        val update = buildUpdate("/all")

        pingAllCommand.execute(bot, update)

        verifyReadinessPoll { it.contains("@$testUsername") }
    }

    @Test
    fun `pingAll should open a readiness poll listing every registered member`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        registerMember(userId = 3L, username = "carol")
        val update = buildUpdate("/all")

        pingAllCommand.execute(bot, update)

        verifyReadinessPoll { it.contains("@alice") && it.contains("@bob") && it.contains("@carol") }
    }

    @Test
    fun `pingAll with readiness disabled should mention all members in a plain message`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        disableChatReadiness()
        val update = buildUpdate("/all")

        pingAllCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") && it.contains("@bob") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `pingAll with trailing text should include it in message`() = runTest {
        registerMember(userId = 1L, username = "alice")
        val update = buildUpdate("/all standup time")

        pingAllCommand.execute(bot, update)

        verifyReadinessPoll { it.contains("standup time") }
    }

    @Test
    fun `all ready-off then ready-on should round-trip the chat flag`() = runTest {
        registerMember(role = MemberRole.MODERATOR)

        pingAllCommand.execute(bot, buildUpdate("/all \$ready-off"))
        assertReadiness(chatEnabled = false)

        pingAllCommand.execute(bot, buildUpdate("/all \$ready-on"))
        assertReadiness(chatEnabled = true)
    }

    @Test
    fun `all ready-off should be refused for a plain member`() = runTest {
        registerMember()

        pingAllCommand.execute(bot, buildUpdate("/all \$ready-off"))

        assertReadiness(chatEnabled = true)
    }

    @Test
    fun `pingGroup should open a readiness poll for the group members`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")
        groupService.addMemberToGroup(testChatId, "devs", "bob")
        val update = buildUpdate("/ping devs")

        pingGroupCommand.execute(bot, update)

        verifyReadinessPoll { it.contains("@alice") && it.contains("@bob") }
    }

    @Test
    fun `pingGroup with readiness disabled for that group should send a plain message`() = runTest {
        registerMember(userId = 1L, username = "alice")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")
        disableGroupReadiness("devs")
        val update = buildUpdate("/ping devs")

        pingGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `ping group ready-off then ready-on should round-trip the group flag`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")

        pingGroupCommand.execute(bot, buildUpdate("/ping devs \$ready-off"))
        assertReadiness(groupEnabled = false)

        pingGroupCommand.execute(bot, buildUpdate("/ping devs \$ready-on"))
        assertReadiness(groupEnabled = true)
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
    fun `ping menu all option should open a readiness poll for every registered member`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")

        pingCallbackHandler().handle(bot, buildCallbackUpdate("${PingCallback.PREFIX}${PingController.ALL_MEMBERS_ID}"))

        verifyReadinessPoll { it.contains("@alice") && it.contains("@bob") }
    }

    @Test
    fun `ping menu group option should poll only that group's members`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")
        val devs = groupService.getAllGroupsWithMembers(testChatId).getOrThrow().first { it.key == "devs" }

        pingCallbackHandler().handle(bot, buildCallbackUpdate("${PingCallback.PREFIX}${devs.id}"))

        verifyReadinessPoll { it.contains("@alice") && !it.contains("@bob") }
    }

    /** A readiness poll is a message that carries the vote keyboard — a plain ping never does. */
    private fun verifyReadinessPoll(text: (String) -> Boolean) {
        verify {
            bot.sendMessage(
                chatId = ChatId.fromId(testChatId),
                text = match(text),
                parseMode = ParseMode.HTML,
                replyMarkup = any<InlineKeyboardMarkup>(),
            )
        }
    }

    private suspend fun assertReadiness(
        chatEnabled: Boolean? = null,
        groupEnabled: Boolean? = null,
    ) {
        chatEnabled?.let { assertEquals(it, chatService.isReadinessEnabled(testChatId).getOrThrow()) }
        groupEnabled?.let {
            assertEquals(it, groupService.getGroupByKey(testChatId, "devs").getOrThrow().readinessEnabled)
        }
    }

    private fun pingCallbackHandler() = PingCallbackHandler(pingController, botAdminUtils, readinessSessionRunner, messagesProvider)
}
