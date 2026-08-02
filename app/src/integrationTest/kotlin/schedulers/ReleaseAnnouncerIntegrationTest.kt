package schedulers

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.data.bot.repository.BotMetaRepositoryImpl
import com.ua.astrumon.data.bot.table.BotMeta
import com.ua.astrumon.data.db.dbQuery
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.ReleaseNote
import com.ua.astrumon.domain.bot.repository.ReleaseNotesRepository
import com.ua.astrumon.domain.bot.service.BotMetaService
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
import com.ua.astrumon.presentation.scheduler.ReleaseAnnouncer
import com.ua.astrumon.presentation.scheduler.SchedulerExceptionHandler
import infrastructure.BaseIntegrationTest
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
 * [ReleaseNotesRepository] is the one deliberate fake. The announcer only broadcasts when
 * `release_notes.json` holds an entry for [VersionInfo.VERSION] with non-empty `changes`; `VERSION`
 * is a compile-time constant whose entry is an internal-only release with `"changes": []`, so the
 * broadcast path is unreachable through the real reader. It is also the only collaborator that can
 * be made to throw, which the scope-containment case needs.
 *
 * `bot_meta` is keyed globally, not per chat, so it is reset here rather than by the base cleaner.
 */
class ReleaseAnnouncerIntegrationTest : BaseIntegrationTest() {
    private class FakeReleaseNotesRepository : ReleaseNotesRepository {
        var result: ResultContainer<List<ReleaseNote>> = ResultContainer.success(emptyList())

        /** Per-language override; any language not listed here gets [result]. */
        val byLanguage = mutableMapOf<BotLanguage, ResultContainer<List<ReleaseNote>>>()
        var throws: Throwable? = null

        override suspend fun getAll(language: BotLanguage): ResultContainer<List<ReleaseNote>> =
            throws?.let { throw it } ?: byLanguage[language] ?: result
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
    fun `an uncaught throw stays inside the scheduler scope and leaves the pointer untouched`() = runTest {
        releaseNotesRepo.throws = IllegalStateException("release notes unreadable")
        botMetaService.setLastNotifiedVersion(olderVersion).getOrThrow()

        val scope = runAnnouncer()

        assertTrue(scope.isActive, "SupervisorJob + SchedulerExceptionHandler must contain the throw")
        verify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        // A throw aborts before the pointer write, so the next startup re-evaluates this version —
        // the opposite of a ResultContainer.Failure, which does advance it (spovishun-134).
        assertEquals(olderVersion, botMetaService.getLastNotifiedVersion().getOrThrow())
        scope.cancel()
    }

    @Test
    fun `the broadcast fans out to every chat the opt-in query returns`() = runTest {
        chatService.ensureChat(secondChatId, "Second chat", "supergroup").getOrThrow()
        botMetaService.setLastNotifiedVersion(olderVersion).getOrThrow()

        runAnnouncer().cancel()

        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(testChatId), text = any(), parseMode = ParseMode.HTML)
        }
        verify(exactly = 1) {
            bot.sendMessage(chatId = ChatId.fromId(secondChatId), text = any(), parseMode = ParseMode.HTML)
        }
        assertEquals(currentVersion, botMetaService.getLastNotifiedVersion().getOrThrow())
    }

    @Test
    fun `each chat is announced to in the language stored for it`() = runTest {
        chatService.ensureChat(secondChatId, "English chat", "supergroup").getOrThrow()
        chatService.setLanguage(secondChatId, BotLanguage.EN).getOrThrow()
        releaseNotesRepo.byLanguage[BotLanguage.EN] = ResultContainer.success(
            listOf(ReleaseNote(currentVersion, "2026-01-01", listOf("New feature"))),
        )
        botMetaService.setLastNotifiedVersion(olderVersion).getOrThrow()

        runAnnouncer().cancel()

        verify(exactly = 1) {
            bot.sendMessage(
                chatId = ChatId.fromId(testChatId),
                text = match { it.contains("Нова можливість") },
                parseMode = ParseMode.HTML,
            )
        }
        verify(exactly = 1) {
            bot.sendMessage(
                chatId = ChatId.fromId(secondChatId),
                text = match { it.contains("New feature") },
                parseMode = ParseMode.HTML,
            )
        }
    }

    /**
     * Drives one `notifyIfNewVersion` pass to completion and hands back the still-live scope so a
     * case can assert it survived. The scope mirrors the production Koin binding, since §8.1 is
     * about a failure staying inside it.
     *
     * Completion is awaited by joining the launched child, not by a delay: the collaborators hit a
     * real database, so a virtual-time dispatcher would report done before the query returned.
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
