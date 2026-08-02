package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.LanguageController
import com.ua.astrumon.presentation.controller.PickerListing
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LanguageControllerTest {
    private val memberService: MemberService = mockk()
    private val chatService: ChatService = mockk()
    private val messagesProvider: BotMessagesProvider = mockk()
    private lateinit var controller: LanguageController

    private val chatId = 123L
    private val userId = 456L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = LanguageController(memberService, chatService, messagesProvider)
        coEvery { messagesProvider.forChat(chatId) } returns BotMessages.of(BotLanguage.UK)
        every { messagesProvider.forLanguage(any()) } answers { BotMessages.of(firstArg()) }
        every { messagesProvider.invalidate(any()) } returns Unit
    }

    @Test
    fun `languageOptions should offer one option per language for a moderator`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true

        val listing = controller.languageOptions(chatId, userId)

        val show = assertIs<PickerListing.Show>(listing)
        assertEquals(BotLanguage.entries.size, show.options.size)
        assertEquals(BotLanguage.entries.map { it.ordinal.toLong() }, show.options.map { it.id })
    }

    @Test
    fun `languageOptions should reject a plain member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val listing = controller.languageOptions(chatId, userId)

        val reject = assertIs<PickerListing.Reject>(listing)
        assertIs<CommandResponse.AccessDenied>(reject.response)
    }

    @Test
    fun `setLanguage should persist the choice and drop the cached bundle`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { chatService.setLanguage(chatId, BotLanguage.EN) } returns ResultContainer.success(Unit)

        val response = controller.setLanguage(chatId, userId, BotLanguage.EN)

        assertIs<CommandResponse.Success>(response)
        coVerify(exactly = 1) { chatService.setLanguage(chatId, BotLanguage.EN) }
        verify(exactly = 1) { messagesProvider.invalidate(chatId) }
    }

    @Test
    fun `setLanguage should confirm in the newly selected language`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { chatService.setLanguage(chatId, BotLanguage.EN) } returns ResultContainer.success(Unit)

        val response = controller.setLanguage(chatId, userId, BotLanguage.EN)

        val success = assertIs<CommandResponse.Success>(response)
        assertEquals(BotMessages.of(BotLanguage.EN).languageSetting.changed(BotLanguage.EN), success.message)
        assertTrue(success.message.contains("English"))
    }

    @Test
    fun `setLanguage should deny a plain member and not write`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val response = controller.setLanguage(chatId, userId, BotLanguage.EN)

        assertIs<CommandResponse.AccessDenied>(response)
        coVerify(exactly = 0) { chatService.setLanguage(any(), any()) }
        verify(exactly = 0) { messagesProvider.invalidate(any()) }
    }

    @Test
    fun `setLanguage should surface a write failure and keep the cache`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { chatService.setLanguage(chatId, BotLanguage.EN) } returns
            ResultContainer.failure(DatabaseException("boom"))

        val response = controller.setLanguage(chatId, userId, BotLanguage.EN)

        assertIs<CommandResponse.Error>(response)
        verify(exactly = 0) { messagesProvider.invalidate(any()) }
    }
}
