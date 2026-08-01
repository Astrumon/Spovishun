package schedulers

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.bot.repository.BirthdayGreetingRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberRepositoryImpl
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.repository.BirthdayGreetingRepository
import com.ua.astrumon.domain.bot.repository.MemberRepository
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.presentation.scheduler.BirthdayGreetingScheduler
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration coverage for [BirthdayGreetingScheduler] over a real PostgreSQL (spovishun-161, audit §8.2).
 *
 * The scheduler fires once a day, so a silent failure stays invisible until someone notices their
 * birthday was skipped. Everything below the scheduler is real: the `birth_md` lookup, the
 * `member_chats` fan-out, and the `birthday_greetings` upsert that makes a re-run idempotent. Time
 * is an injected [Clock.fixed] — the production Koin binding is `Clock.system(ZoneId.of("Europe/Kyiv"))`,
 * and nothing under test reads the wall clock directly.
 *
 * ## Awaiting the catch-up pass
 * `start()` never returns: it runs the catch-up pass, then loops forever on a 24-hour `delay`. Each
 * case therefore completes a [CompletableDeferred] from the *last* repository call that pass makes,
 * through a thin decorator that delegates every other method to the real implementation
 * ([GateOnBirthdayLookup], [GateOnGreetingWrite], [GateOnGreetingCheck]). That is an exact
 * completion signal rather than a wait: virtual time is unusable here because the collaborators
 * suspend on real database IO, which no test scheduler tracks.
 *
 * The scope's exception handler completes the gate exceptionally, so a failure that production
 * swallows inside the background scope surfaces as a test failure instead of a hang.
 */
class BirthdayGreetingSchedulerIntegrationTest : BaseIntegrationTest() {
    /**
     * Fires once the `birth_md` lookup returns — the pass's last act when nobody has a birthday today.
     *
     * [matched] is recorded so the negative case asserts the query itself, not just the absence of a
     * send: were the lookup to match the wrong member, the pass would continue past this gate and a
     * bare `verify(exactly = 0)` could still observe the send-free moment before it happened.
     */
    private class GateOnBirthdayLookup(
        private val delegate: MemberRepository,
        private val gate: CompletableDeferred<Unit>,
    ) : MemberRepository by delegate {
        val matched = mutableListOf<Member>()

        override suspend fun findAllByBirthMd(birthMd: Int): ResultContainer<List<Member>> =
            delegate.findAllByBirthMd(birthMd).also { result ->
                matched += result.getOrNull().orEmpty()
                gate.complete(Unit)
            }
    }

    /** Fires once the greeting has been recorded — the pass's last act when a greeting is sent. */
    private class GateOnGreetingWrite(
        private val delegate: BirthdayGreetingRepository,
        private val gate: CompletableDeferred<Unit>,
    ) : BirthdayGreetingRepository by delegate {
        override suspend fun markSent(
            memberId: Long,
            year: Int,
            sentAt: Instant,
        ): ResultContainer<Unit> = delegate.markSent(memberId, year, sentAt).also { gate.complete(Unit) }
    }

    /**
     * Fires once the already-greeted guard has answered — the pass's last act on a same-day re-run.
     *
     * [answers] is recorded for the same reason as [GateOnBirthdayLookup.matched]: a guard that
     * wrongly answered `false` would let the pass run on past this gate.
     */
    private class GateOnGreetingCheck(
        private val delegate: BirthdayGreetingRepository,
        private val gate: CompletableDeferred<Unit>,
    ) : BirthdayGreetingRepository by delegate {
        val answers = mutableListOf<Boolean>()

        override suspend fun wasSent(
            memberId: Long,
            year: Int,
        ): ResultContainer<Boolean> = delegate.wasSent(memberId, year).also { result ->
            result.getOrNull()?.let { answers += it }
            gate.complete(Unit)
        }
    }

    private val memberRepo = MemberRepositoryImpl()
    private val memberChatRepo = MemberChatRepositoryImpl()
    private val greetingRepo = BirthdayGreetingRepositoryImpl()
    private val birthdayService = BirthdayService(memberRepo, memberChatRepo, greetingRepo)

    // Distinct from the dates BirthdayCommandIntegrationTest uses (25.12 / 01.01): findAllByBirthMd
    // is a global query, not scoped to testChatId.
    private val year = 2026
    private val todayDate = BirthDate(17, 7)
    private val otherDate = BirthDate(18, 7)
    private val secondChatId = testChatId - 1L
    private val fixedClock: Clock = Clock.fixed(
        LocalDateTime.of(year, todayDate.month, todayDate.day, RUN_HOUR, 0).atZone(KYIV).toInstant(),
        KYIV,
    )

    @AfterEach
    fun cleanUpSecondChat() = runBlocking { cleaner.cleanupByChatId(secondChatId) }

    @Test
    fun `a member whose birthday is today is greeted and recorded`() = runTest {
        val member = registerMember()
        birthdayService.setBirthday(member.userId, todayDate).getOrThrow()
        val gate = CompletableDeferred<Unit>()

        runCatchUpPass(gate, BirthdayService(memberRepo, memberChatRepo, GateOnGreetingWrite(greetingRepo, gate)))

        verify(exactly = 1) {
            bot.sendMessage(
                chatId = ChatId.fromId(testChatId),
                text = match { it.contains("@$testUsername") },
                parseMode = ParseMode.HTML,
            )
        }
        assertTrue(
            birthdayService.wasGreetedThisYear(member.id, year).getOrThrow(),
            "the greeting must be recorded in birthday_greetings",
        )
    }

    @Test
    fun `a member whose birthday is another day is not greeted`() = runTest {
        val member = registerMember()
        birthdayService.setBirthday(member.userId, otherDate).getOrThrow()
        val gate = CompletableDeferred<Unit>()
        val lookup = GateOnBirthdayLookup(memberRepo, gate)

        runCatchUpPass(gate, BirthdayService(lookup, memberChatRepo, greetingRepo))

        assertTrue(lookup.matched.isEmpty(), "today's birth_md lookup must not match a member born another day")
        verify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        assertFalse(birthdayService.wasGreetedThisYear(member.id, year).getOrThrow())
    }

    @Test
    fun `a second run on the same day sends no second greeting`() = runTest {
        val member = registerMember()
        birthdayService.setBirthday(member.userId, todayDate).getOrThrow()

        val firstPass = CompletableDeferred<Unit>()
        runCatchUpPass(
            firstPass,
            BirthdayService(memberRepo, memberChatRepo, GateOnGreetingWrite(greetingRepo, firstPass)),
        )
        val secondPass = CompletableDeferred<Unit>()
        val guard = GateOnGreetingCheck(greetingRepo, secondPass)
        runCatchUpPass(secondPass, BirthdayService(memberRepo, memberChatRepo, guard))

        assertEquals(listOf(true), guard.answers, "the second pass must be stopped by the already-greeted guard")
        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(testChatId), text = any(), parseMode = ParseMode.HTML)
        }
        assertTrue(birthdayService.wasGreetedThisYear(member.id, year).getOrThrow())
    }

    @Test
    fun `the greeting reaches every chat the member belongs to`() = runTest {
        val member = registerMember()
        registerMember(chatId = secondChatId)
        birthdayService.setBirthday(member.userId, todayDate).getOrThrow()
        val gate = CompletableDeferred<Unit>()

        runCatchUpPass(gate, BirthdayService(memberRepo, memberChatRepo, GateOnGreetingWrite(greetingRepo, gate)))

        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(testChatId), text = any(), parseMode = ParseMode.HTML)
        }
        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(secondChatId), text = any(), parseMode = ParseMode.HTML)
        }
    }

    /**
     * Starts the scheduler on a scope shaped like the production Koin binding, waits for the
     * catch-up pass, then cancels — `start()`'s daily loop would otherwise keep the coroutine
     * parked for the rest of the run.
     */
    private suspend fun runCatchUpPass(
        gate: CompletableDeferred<Unit>,
        service: BirthdayService,
    ) {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, throwable -> gate.completeExceptionally(throwable) } +
                CoroutineName("birthday-scheduler"),
        )
        BirthdayGreetingScheduler(service, fixedClock, scope).start(bot)
        gate.await()
        scope.cancel()
    }

    private companion object {
        val KYIV: ZoneId = ZoneId.of("Europe/Kyiv")

        // 14:00 Kyiv — past the scheduler's 12:00 target, so start()'s catch-up pass runs immediately.
        const val RUN_HOUR = 14
    }
}
