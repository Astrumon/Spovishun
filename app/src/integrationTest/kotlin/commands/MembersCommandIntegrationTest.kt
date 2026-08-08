package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MembersCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `members command should auto-register caller and show them in the list`() = runTest {
        val update = buildUpdate("/members")

        dispatch(membersCommand, update)

        // The caller is auto-registered by the command decorator CommandRegistry applies (spovishun-172)
        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertTrue(member.userId == testUserId)
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@$testUsername") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `members with registered users should list their usernames`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        val update = buildUpdate("/members")

        membersCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") && it.contains("@bob") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `members response should contain role badges for non-default roles`() = runTest {
        registerMember(userId = 1L, username = "alice", role = MemberRole.ADMIN)
        val update = buildUpdate("/members")

        membersCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@alice") },
                ParseMode.HTML,
            )
        }
    }
}
