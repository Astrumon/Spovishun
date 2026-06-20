package commands

import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseE2ETest
import infrastructure.E2EConfig
import infrastructure.E2EDbConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E tests for a user who belongs to two separate chats simultaneously.
 *
 * Uses [testChatId] as chat A (from env) and a synthetic [secondChatId] as chat B.
 * Both chats are backed by the real dev DB; no Telegram API calls are needed
 * because registration goes through [autoRegisterService] directly.
 */
class MultiChatUserE2ETest : BaseE2ETest() {
    // Synthetic second chat — no real Telegram group required; only DB-level testing
    private val secondChatId: Long get() = testChatId - 1L

    @AfterEach
    fun cleanupSecondChat() {
        if (!E2EConfig.isConfigured || !E2EDbConfig.isConfigured) return
        runBlocking { cleaner.cleanupByChatId(secondChatId) }
    }

    @Test
    fun `user registered in two chats has one member row and two member_chat rows`() {
        val userId = 900001L
        registerMember(userId, "multichatuser", "MultiChat")

        // Register same user in the second chat
        runBlocking {
            autoRegisterService
                .ensureUserRegistered(
                    chatId = secondChatId,
                    userId = userId,
                    username = "multichatuser",
                    firstName = "MultiChat",
                    userRole = MemberRole.MEMBER,
                ).getOrThrow()
        }

        val inChatA = allMembers()
        val inChatB = runBlocking { memberService.getAllMembersInChat(secondChatId).getOrThrow() }

        assertTrue(inChatA.any { it.userId == userId }, "User must be visible in chat A")
        assertTrue(inChatB.any { it.userId == userId }, "User must be visible in chat B")
        assertEquals(1, inChatA.count { it.userId == userId }, "Exactly one entry in chat A")
        assertEquals(1, inChatB.count { it.userId == userId }, "Exactly one entry in chat B")
    }

    @Test
    fun `members of chat A are not visible in chat B`() {
        val userAOnly = 900002L
        val userBoth = 900003L
        registerMember(userAOnly, "onlychata", "OnlyChatA")
        registerMember(userBoth, "bothchats", "BothChats")

        runBlocking {
            autoRegisterService
                .ensureUserRegistered(
                    chatId = secondChatId,
                    userId = userBoth,
                    username = "bothchats",
                    firstName = "BothChats",
                    userRole = MemberRole.MEMBER,
                ).getOrThrow()
        }

        val inChatB = runBlocking { memberService.getAllMembersInChat(secondChatId).getOrThrow() }

        assertFalse(inChatB.any { it.userId == userAOnly }, "Chat-A-only user must not appear in chat B")
        assertTrue(inChatB.any { it.userId == userBoth }, "User in both chats must appear in chat B")
    }

    @Test
    fun `role in chat A is independent of role in chat B`() {
        val userId = 900004L
        registerMember(userId, "rolediffuser", "RoleDiff", MemberRole.ADMIN)

        runBlocking {
            autoRegisterService
                .ensureUserRegistered(
                    chatId = secondChatId,
                    userId = userId,
                    username = "rolediffuser",
                    firstName = "RoleDiff",
                    userRole = MemberRole.MEMBER,
                ).getOrThrow()
        }

        val inChatA = allMembers().first { it.userId == userId }
        val inChatB = runBlocking { memberService.getAllMembersInChat(secondChatId).getOrThrow() }
            .first { it.userId == userId }

        assertEquals(MemberRole.ADMIN, inChatA.role, "Role in chat A must be ADMIN")
        assertEquals(MemberRole.MEMBER, inChatB.role, "Role in chat B must be MEMBER")
    }

    @Test
    fun `cleaning up chat A preserves user membership in chat B`() {
        val userId = 900005L
        registerMember(userId, "persistuser", "Persist")

        runBlocking {
            autoRegisterService
                .ensureUserRegistered(
                    chatId = secondChatId,
                    userId = userId,
                    username = "persistuser",
                    firstName = "Persist",
                    userRole = MemberRole.MEMBER,
                ).getOrThrow()
        }

        // Simulate what @AfterTest does for chat A
        runBlocking { cleaner.cleanupByChatId(testChatId) }

        val inChatB = runBlocking { memberService.getAllMembersInChat(secondChatId).getOrThrow() }
        assertTrue(
            inChatB.any { it.userId == userId },
            "User must still be in chat B after chat A cleanup",
        )
    }
}
