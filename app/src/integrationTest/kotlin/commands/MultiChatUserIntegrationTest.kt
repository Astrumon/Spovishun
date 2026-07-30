package commands

import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseIntegrationTest
import infrastructure.IntegrationDbConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A user who belongs to two chats at once: one `members` row, one `member_chats` row per chat,
 * with roles kept independent.
 *
 * Lives at the integration layer on purpose. Every assertion here is about rows in PostgreSQL —
 * nothing reaches Telegram, and the second chat is synthetic, so the real API has nothing to say
 * about it. It used to sit in the e2e source set, where it paid for real bot tokens and a
 * serialized CI lane to prove exactly the same thing (spovishun-160).
 */
class MultiChatUserIntegrationTest : BaseIntegrationTest() {
    // Synthetic second chat — DB-level only, no real Telegram group behind it.
    private val secondChatId = testChatId - 1L

    @AfterEach
    fun cleanupSecondChat() {
        if (!IntegrationDbConfig.isConfigured) return
        runBlocking { cleaner.cleanupByChatId(secondChatId) }
    }

    @Test
    fun `user registered in two chats has one member row and two member_chat rows`() = runTest {
        val userId = 900001L
        registerMember(userId = userId, username = "multichatuser", firstName = "MultiChat")
        registerMember(userId = userId, username = "multichatuser", firstName = "MultiChat", chatId = secondChatId)

        val inChatA = memberService.getAllMembersInChat(testChatId).getOrThrow()
        val inChatB = memberService.getAllMembersInChat(secondChatId).getOrThrow()

        assertTrue(inChatA.any { it.userId == userId }, "User must be visible in chat A")
        assertTrue(inChatB.any { it.userId == userId }, "User must be visible in chat B")
        assertEquals(1, inChatA.count { it.userId == userId }, "Exactly one entry in chat A")
        assertEquals(1, inChatB.count { it.userId == userId }, "Exactly one entry in chat B")
    }

    @Test
    fun `members of chat A are not visible in chat B`() = runTest {
        val userAOnly = 900002L
        val userBoth = 900003L
        registerMember(userId = userAOnly, username = "onlychata", firstName = "OnlyChatA")
        registerMember(userId = userBoth, username = "bothchats", firstName = "BothChats")
        registerMember(userId = userBoth, username = "bothchats", firstName = "BothChats", chatId = secondChatId)

        val inChatB = memberService.getAllMembersInChat(secondChatId).getOrThrow()

        assertFalse(inChatB.any { it.userId == userAOnly }, "Chat-A-only user must not appear in chat B")
        assertTrue(inChatB.any { it.userId == userBoth }, "User in both chats must appear in chat B")
    }

    @Test
    fun `role in chat A is independent of role in chat B`() = runTest {
        val userId = 900004L
        registerMember(userId = userId, username = "rolediffuser", firstName = "RoleDiff", role = MemberRole.ADMIN)
        registerMember(
            userId = userId,
            username = "rolediffuser",
            firstName = "RoleDiff",
            chatId = secondChatId,
            role = MemberRole.MEMBER,
        )

        val inChatA = memberService.getAllMembersInChat(testChatId).getOrThrow().first { it.userId == userId }
        val inChatB = memberService.getAllMembersInChat(secondChatId).getOrThrow().first { it.userId == userId }

        assertEquals(MemberRole.ADMIN, inChatA.role, "Role in chat A must be ADMIN")
        assertEquals(MemberRole.MEMBER, inChatB.role, "Role in chat B must be MEMBER")
    }

    @Test
    fun `cleaning up chat A preserves user membership in chat B`() = runTest {
        val userId = 900005L
        registerMember(userId = userId, username = "persistuser", firstName = "Persist")
        registerMember(userId = userId, username = "persistuser", firstName = "Persist", chatId = secondChatId)

        cleaner.cleanupByChatId(testChatId)

        val inChatB = memberService.getAllMembersInChat(secondChatId).getOrThrow()
        assertTrue(inChatB.any { it.userId == userId }, "User must still be in chat B after chat A cleanup")
    }
}
