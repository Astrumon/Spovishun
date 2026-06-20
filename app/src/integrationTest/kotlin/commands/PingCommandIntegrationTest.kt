package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
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
}
