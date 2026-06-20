package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.data.bot.repository.BirthdayGreetingRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberRepositoryImpl
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.presentation.bot.commands.BirthdayCommand
import com.ua.astrumon.presentation.controller.BirthdayController
import infrastructure.BaseIntegrationTest
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BirthdayCommandIntegrationTest : BaseIntegrationTest() {
    private val memberRepo = MemberRepositoryImpl()
    private val memberChatRepo = MemberChatRepositoryImpl()
    private val birthdayGreetingRepo = BirthdayGreetingRepositoryImpl()

    private lateinit var birthdayService: BirthdayService
    private lateinit var birthdayController: BirthdayController
    private lateinit var birthdayCommand: BirthdayCommand

    private val modUserId = 333L
    private val modUsername = "moduser"

    @BeforeTest
    fun setupBirthday() {
        birthdayService = BirthdayService(memberRepo, memberChatRepo, birthdayGreetingRepo)
        birthdayController = BirthdayController(birthdayService, memberService)
        birthdayCommand = BirthdayCommand(birthdayController)
    }

    @Test
    fun `birthday set should save birthday for own user`() = runTest {
        registerMember()
        val update = buildUpdate("/birthday 25.12")

        birthdayCommand.execute(bot, update)

        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertNotNull(member.birthday)
        assertTrue(member.birthday == BirthDate(25, 12))
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("25.12") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `birthday off should clear saved birthday`() = runTest {
        registerMember()
        birthdayService.setBirthday(testUserId, BirthDate(25, 12))

        val update = buildUpdate("/birthday off")
        birthdayCommand.execute(bot, update)

        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertNull(member.birthday)
    }

    @Test
    fun `birthday should reject invalid date format`() = runTest {
        registerMember()
        val update = buildUpdate("/birthday 99.99")

        birthdayCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Невірний") || it.contains("формат") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `moderator can set birthday for another registered user`() = runTest {
        registerMember()
        registerMember(userId = modUserId, username = modUsername, firstName = "Mod", role = MemberRole.MODERATOR)
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MODERATOR

        val update = buildUpdate(
            text = "/birthday 01.01 @$testUsername",
            userId = modUserId,
            username = modUsername,
            firstName = "Mod",
        )
        birthdayCommand.execute(bot, update)

        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertNotNull(member.birthday)
        assertTrue(member.birthday == BirthDate(1, 1))
    }

    @Test
    fun `non-moderator cannot set birthday for another user`() = runTest {
        registerMember()
        val update = buildUpdate("/birthday 01.01 @other")

        birthdayCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("адмін") || it.contains("модератор") || it.contains("Лише") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `birthday set for unregistered user returns user not registered message`() = runTest {
        registerMember(userId = modUserId, username = modUsername, firstName = "Mod", role = MemberRole.MODERATOR)

        val update = buildUpdate(
            text = "/birthday 25.12 @nonexistent",
            userId = modUserId,
            username = modUsername,
            firstName = "Mod",
        )
        birthdayCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("nonexistent") || it.contains("не зареєстрований") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `markSent called twice should not throw — upsert is idempotent`() = runTest {
        registerMember()
        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        val now = Clock.System.now()

        val result1 = birthdayGreetingRepo.markSent(member.id, 2025, now)
        val result2 = birthdayGreetingRepo.markSent(member.id, 2025, now)

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)

        val greeted = birthdayGreetingRepo.wasSent(member.id, 2025).getOrThrow()
        assertTrue(greeted)
    }

    @Test
    fun `wasSent returns false before markSent`() = runTest {
        registerMember()
        val member = memberService.getMemberByUsername(testUsername).getOrThrow()

        val result = birthdayGreetingRepo.wasSent(member.id, 2025).getOrThrow()

        assertFalse(result)
    }
}
