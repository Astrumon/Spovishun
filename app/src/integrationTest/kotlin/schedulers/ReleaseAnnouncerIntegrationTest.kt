package schedulers

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.data.bot.repository.BotMetaRepositoryImpl
import com.ua.astrumon.data.bot.table.BotMeta
import com.ua.astrumon.data.db.dbQuery
import com.ua.astrumon.domain.bot.model.ReleaseNote
import com.ua.astrumon.domain.bot.repository.ReleaseNotesRepository
import com.ua.astrumon.domain.bot.service.BotMetaService
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
import com.ua.astrumon.presentation.scheduler.ReleaseAnnouncer
import com.ua.astrumon.presentation.scheduler.SchedulerExceptionHandler
import infrastructure.BaseIntegrationTest
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration coverage for [ReleaseAnnouncer] over a real PostgreSQL (spovishun-161, audit §8.1).
 *
 * The announcer posts to every opted-in chat on startup after a version bump, so a defect reaches
 * all users at once — and its failure mode is an exception swallowed inside a background scope.
 * Both DB-backed collaborators are real here: the opt-in query (`chats.announcements_enabled`) and
 * the already-announced guard (`bot_meta.last_notified_version`).
 *
 * ## The one deliberate stub
 * [ReleaseNotesRepository] is a hand-written fake rather than the real classpath-resource reader.
 * The announcer only broadcasts when `release_notes.json` holds an entry for [VersionInfo.VERSION]
 * with non-empty `changes`; `VERSION` is a compile-time constant and the matching entry is an
 * internal-only release with `"changes": []`, so the broadcast path is unreachable through the real
 * implementation. That read is a static resource already covered by unit tests — it is not what
 * §8.1 asks to prove.
 *
 * `bot_meta` is keyed globally, not per chat, so it is reset here rather than by the base cleaner;
 * the second chat is cleaned explicitly, the pattern `MultiChatUserIntegrationTest` established for
 * a chat outside `testChatId`.
 */
class ReleaseAnnouncerIntegrationTest : BaseIntegrationTest() {
    private class FakeReleaseNotesRepository : ReleaseNotesRepository {
        var result: ResultContainer<List<ReleaseNote>> = ResultContainer.success(emptyList())

        override suspend fun getAll(): ResultContainer<List<ReleaseNote>> = result
    }

    private val currentVersion = VersionInfo.VERSION
    private val olderVersion = "0.0.1"
    private val secondChatId = testChatId - 1L

    private lateinit var releaseNotesRepo: FakeReleaseNotesRepository
    private lateinit var botMetaService: BotMetaService
    private lateinit var releaseNotesService: ReleaseNotesService

    @BeforeTest
    fun setUpAnnouncer() {
        releaseNotesRepo = FakeReleaseNotesRepository()
        releaseNotesRepo.result = ResultContainer.success(
            listOf(ReleaseNote(currentVersion, "2026-01-01", listOf("Нова можливість"))),
        )
        releaseNotesService = ReleaseNotesService(releaseNotesRepo)
        botMetaService = BotMetaService(BotMetaRepositoryImpl())
        runBlocking {
            dbQuery { BotMeta.deleteAll() }
            chatService.ensureChat(testChatId, "Test chat", "supergroup").getOrThrow()
        }
    }

    @AfterEach
    fun cleanUpAnnouncer() = runBlocking {
        dbQuery { BotMeta.deleteAll() }
        cleaner.cleanupByChatId(secondChatId)
    }

    @Test
    fun `only chats with announcements enabled receive the broadcast`() = runTest {
        chatService.ensureChat(secondChatId, "Opted out", "supergroup").getOrThrow()
        chatService.setAnnouncementsEnabled(secondChatId, false).getOrThrow()
        botMetaService.setLastNotifiedVersion(olderVersion).getOrThrow()

        runAnnouncer().cancel()

        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(testChatId), text = any(), parseMode = ParseMode.HTML)
        }
        verify(exactly = 0) {
            bot.sendMessage(chatId = ChatId.fromId(secondChatId), text = any(), parseMode = ParseMode.HTML)
        }
        assertEquals(currentVersion, botMetaService.getLastNotifiedVersion().getOrThrow())
    }

    @Test
    fun `the already-announced guard prevents a second broadcast for the same version`() = runTest {
        botMetaService.setLastNotifiedVersion(olderVersion).getOrThrow()

        runAnnouncer().cancel()
        runAnnouncer().cancel()

        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(testChatId), text = any(), parseMode = ParseMode.HTML)
        }
        assertEquals(currentVersion, botMetaService.getLastNotifiedVersion().getOrThrow())
    }

    @Test
    fun `the first ever run stores the version without broadcasting`() = runTest {
        assertNull(botMetaService.getLastNotifiedVersion().getOrThrow())

        runAnnouncer().cancel()

        verify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        assertEquals(currentVersion, botMetaService.getLastNotifiedVersion().getOrThrow())
    }

    @Test
    fun `a release notes failure does not propagate out of the scheduler scope`() = runTest {
        releaseNotesRepo.result = ResultContainer.failure(DatabaseException("read error"))
        botMetaService.setLastNotifiedVersion(olderVersion).getOrThrow()

        val scope = runAnnouncer()

        assertTrue(scope.isActive, "a swallowed repository failure must not cancel the announcer scope")
        verify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        // Documented behaviour (spovishun-134): a read failure is indistinguishable from "no entry",
        // so the pointer advances rather than re-evaluating this version on every restart.
        assertEquals(currentVersion, botMetaService.getLastNotifiedVersion().getOrThrow())
        scope.cancel()
    }

    @Test
    fun `a send failure to one chat does not stop the others`() = runTest {
        chatService.ensureChat(secondChatId, "Second chat", "supergroup").getOrThrow()
        botMetaService.setLastNotifiedVersion(olderVersion).getOrThrow()
        every {
            bot.sendMessage(chatId = ChatId.fromId(secondChatId), text = any(), parseMode = ParseMode.HTML)
        } throws RuntimeException("Telegram error")

        runAnnouncer().cancel()

        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(testChatId), text = any(), parseMode = ParseMode.HTML)
        }
        assertEquals(currentVersion, botMetaService.getLastNotifiedVersion().getOrThrow())
    }

    /**
     * Drives one `notifyIfNewVersion` pass to completion and hands back the still-live scope so a
     * case can assert it survived.
     *
     * The scope mirrors the production Koin binding (`SupervisorJob` + `Dispatchers.Default` +
     * [SchedulerExceptionHandler] + [CoroutineName]) — the point of §8.1 is that a failure stays
     * inside it. Completion is awaited by joining the launched child, not by a delay: the
     * collaborators here hit a real database, so a virtual-time dispatcher would report done before
     * the query returned.
     */
    private suspend fun runAnnouncer(): CoroutineScope {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + SchedulerExceptionHandler.create() + CoroutineName("release-announcer"),
        )
        ReleaseAnnouncer(releaseNotesService, botMetaService, chatService, scope).notifyIfNewVersion(bot)
        scope.coroutineContext.job.children
            .toList()
            .joinAll()
        return scope
    }
}
