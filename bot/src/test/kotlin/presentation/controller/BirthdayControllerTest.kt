package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.BirthdayController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BirthdayControllerTest {
    private val birthdayService: BirthdayService = mockk()
    private val memberService: MemberService = mockk()
    private lateinit var controller: BirthdayController

    private val chatId = -100L
    private val userId = 456L
    private val member = Member(1L, userId, "alice", "Alice", BirthDate(25, 12))

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = BirthdayController(birthdayService, memberService)
    }

    // --- setOwnBirthday ---

    @Test
    fun `setOwnBirthday should return Error on invalid date token`() = runTest {
        val result = controller.setOwnBirthday(userId, "99.99")

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `setOwnBirthday should return Error on wrong format`() = runTest {
        val result = controller.setOwnBirthday(userId, "25-12")

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `setOwnBirthday should return Success when service succeeds`() = runTest {
        coEvery { birthdayService.setBirthday(userId, BirthDate(25, 12)) } returns ResultContainer.success(member)

        val result = controller.setOwnBirthday(userId, "25.12")

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("25.12"))
    }

    @Test
    fun `setOwnBirthday should return Error when service fails`() = runTest {
        coEvery { birthdayService.setBirthday(userId, BirthDate(1, 1)) } returns
            ResultContainer.failure(DatabaseException("DB error"))

        val result = controller.setOwnBirthday(userId, "01.01")

        assertTrue(result is CommandResponse.Error)
    }

    // --- clearOwnBirthday ---

    @Test
    fun `clearOwnBirthday should return Success when service succeeds`() = runTest {
        coEvery { birthdayService.clearBirthday(userId) } returns ResultContainer.success(member.copy(birthday = null))

        val result = controller.clearOwnBirthday(userId)

        assertTrue(result is CommandResponse.Success)
    }

    @Test
    fun `clearOwnBirthday should return Error when service fails`() = runTest {
        coEvery { birthdayService.clearBirthday(userId) } returns
            ResultContainer.failure(DatabaseException("DB error"))

        val result = controller.clearOwnBirthday(userId)

        assertTrue(result is CommandResponse.Error)
    }

    // --- setBirthdayForOther ---

    @Test
    fun `setBirthdayForOther should return AccessDenied for non-moderator`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = controller.setBirthdayForOther(chatId, userId, listOf("01.01", "@bob"))

        assertTrue(result is CommandResponse.AccessDenied)
    }

    @Test
    fun `setBirthdayForOther should return Error when less than 2 args`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true

        val result = controller.setBirthdayForOther(chatId, userId, listOf("01.01"))

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `setBirthdayForOther should return Error on invalid date`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true

        val result = controller.setBirthdayForOther(chatId, userId, listOf("99.99", "@bob"))

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `setBirthdayForOther should return Error when username is not parseable`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true

        val result = controller.setBirthdayForOther(chatId, userId, listOf("01.01", "###"))

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `setBirthdayForOther should return Error when user not registered`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { birthdayService.setBirthdayForUsername("bob", BirthDate(1, 1)) } returns
            ResultContainer.failure(ResourceNotFoundException("Member", "bob"))

        val result = controller.setBirthdayForOther(chatId, userId, listOf("01.01", "@bob"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("bob"))
    }

    @Test
    fun `setBirthdayForOther should return Success when moderator sets birthday for existing user`() = runTest {
        val bob = Member(2L, 789L, "bob", "Bob", BirthDate(1, 1))
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { birthdayService.setBirthdayForUsername("bob", BirthDate(1, 1)) } returns ResultContainer.success(bob)

        val result = controller.setBirthdayForOther(chatId, userId, listOf("01.01", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("01.01"))
    }

    @Test
    fun `setBirthdayForOther should return Error when service fails with non-ResourceNotFoundException`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { birthdayService.setBirthdayForUsername("bob", BirthDate(1, 1)) } returns
            ResultContainer.failure(DatabaseException("DB error"))

        val result = controller.setBirthdayForOther(chatId, userId, listOf("01.01", "@bob"))

        assertTrue(result is CommandResponse.Error)
    }
}
