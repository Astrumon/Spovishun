package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.model.MemberRole
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GrantRoleCommandIntegrationTest : BaseIntegrationTest() {

    @Test
    fun `grantrole as non-admin should be denied`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        val update = buildUpdate("/grantrole @alice moderator")

        grantRoleCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Лише адміни") },
                ParseMode.HTML
            )
        }
    }

    @Test
    fun `grantrole as admin should update target member role`() = runTest {
        registerMember(userId = testAdminId, username = testAdminUsername, role = MemberRole.ADMIN)
        registerMember(userId = 2L, username = "alice", role = MemberRole.MEMBER)
        val update = buildUpdate(
            "/grantrole @alice moderator",
            userId = testAdminId,
            username = testAdminUsername
        )

        grantRoleCommand.execute(bot, update)

        val alice = memberService.getMemberByUsername("alice").getOrThrow()
        assertEquals(MemberRole.MODERATOR, alice.role)
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("alice") && it.contains("moderator") },
                ParseMode.HTML
            )
        }
    }

    @Test
    fun `grantrole for non-existent user should report user not found`() = runTest {
        registerMember(userId = testAdminId, username = testAdminUsername, role = MemberRole.ADMIN)
        val update = buildUpdate(
            "/grantrole @nobody admin",
            userId = testAdminId,
            username = testAdminUsername
        )

        grantRoleCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("не знайдено") },
                ParseMode.HTML
            )
        }
    }

    @Test
    fun `grantrole with invalid role string should report unknown role`() = runTest {
        registerMember(userId = testAdminId, username = testAdminUsername, role = MemberRole.ADMIN)
        registerMember(userId = 2L, username = "alice")
        val update = buildUpdate(
            "/grantrole @alice superadmin",
            userId = testAdminId,
            username = testAdminUsername
        )

        grantRoleCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Невідома роль") || it.contains("superadmin") },
                ParseMode.HTML
            )
        }
    }
}
