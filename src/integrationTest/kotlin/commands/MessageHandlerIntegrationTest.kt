package commands

import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageHandlerIntegrationTest : BaseIntegrationTest() {

    @Test
    fun `text message from new user should register them in the repository`() = runTest {
        val update = buildUpdate("Hello world")

        messageHandler.handleIncomingMessage(bot, update)

        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertTrue(member.userId == testUserId)
        assertTrue(member.chatId == testChatId)
    }

    @Test
    fun `text message from already registered user should not create duplicate`() = runTest {
        registerMember()
        val allBefore = memberService.getAllMembers().getOrThrow()
        val update = buildUpdate("Hello again")

        messageHandler.handleIncomingMessage(bot, update)

        val allAfter = memberService.getAllMembers().getOrThrow()
        assertEquals(allBefore.size, allAfter.size)
    }

    @Test
    fun `message handler should not send any reply to the chat`() = runTest {
        val update = buildUpdate("Some text")

        messageHandler.handleIncomingMessage(bot, update)

        verify(exactly = 0) { bot.sendMessage(any(), any<String>(), any()) }
    }
}
