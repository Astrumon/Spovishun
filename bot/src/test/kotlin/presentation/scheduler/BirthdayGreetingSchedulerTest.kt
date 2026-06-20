package presentation.scheduler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.presentation.scheduler.BirthdayGreetingScheduler
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test

class BirthdayGreetingSchedulerTest {
    private val birthdayService: BirthdayService = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private val zone: ZoneId = ZoneId.of("Europe/Kyiv")

    private val memberAlice = Member(1L, 100L, "alice", "Alice", BirthDate(25, 12))
    private val memberBob = Member(2L, 200L, "bob", "Bob", BirthDate(25, 12))

    @BeforeTest
    fun setup() {
        clearAllMocks()
    }

    private fun clockAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 14,
    ): Clock {
        val instant = ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant()
        return Clock.fixed(instant, zone)
    }

    // --- catch-up on start ---

    @Test
    fun `start should immediately run catch-up for today's date`() = runTest {
        val clock = clockAt(2025, 12, 25)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(25, 12)) } returns ResultContainer.success(emptyList())

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify { birthdayService.getMembersWithBirthday(BirthDate(25, 12)) }
        scope.cancel()
    }

    @Test
    fun `greet should send message to all registered chats for member`() = runTest {
        val clock = clockAt(2025, 12, 25)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(25, 12)) } returns ResultContainer.success(listOf(memberAlice))
        coEvery { birthdayService.wasGreetedThisYear(1L, 2025) } returns ResultContainer.success(false)
        coEvery { birthdayService.findChatIdsForMember(1L) } returns ResultContainer.success(listOf(-100L, -200L))
        coEvery { birthdayService.recordGreetingSent(1L, 2025, any()) } returns ResultContainer.success(Unit)

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify { bot.sendMessage(chatId = ChatId.fromId(-100L), text = any(), parseMode = ParseMode.HTML) }
        coVerify { bot.sendMessage(chatId = ChatId.fromId(-200L), text = any(), parseMode = ParseMode.HTML) }
        scope.cancel()
    }

    @Test
    fun `greet should record greeting after sending messages`() = runTest {
        val clock = clockAt(2025, 12, 25)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(25, 12)) } returns ResultContainer.success(listOf(memberAlice))
        coEvery { birthdayService.wasGreetedThisYear(1L, 2025) } returns ResultContainer.success(false)
        coEvery { birthdayService.findChatIdsForMember(1L) } returns ResultContainer.success(listOf(-100L))
        coEvery { birthdayService.recordGreetingSent(1L, 2025, any<Instant>()) } returns ResultContainer.success(Unit)

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify { birthdayService.recordGreetingSent(1L, 2025, any()) }
        scope.cancel()
    }

    @Test
    fun `greet should skip member already greeted this year`() = runTest {
        val clock = clockAt(2025, 12, 25)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(25, 12)) } returns ResultContainer.success(listOf(memberAlice))
        coEvery { birthdayService.wasGreetedThisYear(1L, 2025) } returns ResultContainer.success(true)

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        coVerify(exactly = 0) { birthdayService.recordGreetingSent(any(), any(), any()) }
        scope.cancel()
    }

    @Test
    fun `greet should skip member with no registered chats`() = runTest {
        val clock = clockAt(2025, 12, 25)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(25, 12)) } returns ResultContainer.success(listOf(memberAlice))
        coEvery { birthdayService.wasGreetedThisYear(1L, 2025) } returns ResultContainer.success(false)
        coEvery { birthdayService.findChatIdsForMember(1L) } returns ResultContainer.success(emptyList())

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        scope.cancel()
    }

    @Test
    fun `greet should continue to next member when one fails`() = runTest {
        val clock = clockAt(2025, 12, 25)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(25, 12)) } returns
            ResultContainer.success(listOf(memberAlice, memberBob))

        coEvery { birthdayService.wasGreetedThisYear(1L, 2025) } returns ResultContainer.success(false)
        coEvery { birthdayService.findChatIdsForMember(1L) } returns ResultContainer.success(listOf(-100L))
        every { bot.sendMessage(chatId = ChatId.fromId(-100L), text = any(), parseMode = ParseMode.HTML) } throws
            RuntimeException("Telegram error")

        coEvery { birthdayService.wasGreetedThisYear(2L, 2025) } returns ResultContainer.success(false)
        coEvery { birthdayService.findChatIdsForMember(2L) } returns ResultContainer.success(listOf(-200L))
        coEvery { birthdayService.recordGreetingSent(2L, 2025, any()) } returns ResultContainer.success(Unit)

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify { bot.sendMessage(chatId = ChatId.fromId(-200L), text = any(), parseMode = ParseMode.HTML) }
        coVerify { birthdayService.recordGreetingSent(2L, 2025, any()) }
        scope.cancel()
    }

    @Test
    fun `start should also process feb 29 birthdays on feb 28 in non-leap year`() = runTest {
        val clock = clockAt(2025, 2, 28) // 2025 is not a leap year
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(28, 2)) } returns ResultContainer.success(emptyList())
        coEvery { birthdayService.getMembersWithBirthday(BirthDate(29, 2)) } returns ResultContainer.success(emptyList())

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify { birthdayService.getMembersWithBirthday(BirthDate(28, 2)) }
        coVerify { birthdayService.getMembersWithBirthday(BirthDate(29, 2)) }
        scope.cancel()
    }

    @Test
    fun `start should not process feb 29 birthdays on feb 28 in leap year`() = runTest {
        val clock = clockAt(2028, 2, 28) // 2028 is a leap year
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = BirthdayGreetingScheduler(birthdayService, clock, scope)

        coEvery { birthdayService.getMembersWithBirthday(BirthDate(28, 2)) } returns ResultContainer.success(emptyList())

        scheduler.start(bot)
        testScheduler.runCurrent()

        coVerify { birthdayService.getMembersWithBirthday(BirthDate(28, 2)) }
        coVerify(exactly = 0) { birthdayService.getMembersWithBirthday(BirthDate(29, 2)) }
        scope.cancel()
    }
}
