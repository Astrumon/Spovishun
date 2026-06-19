package presentation.scheduler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.model.ReleaseNote
import com.ua.astrumon.domain.service.BotMetaService
import com.ua.astrumon.domain.service.ChatService
import com.ua.astrumon.domain.service.ReleaseNotesService
import com.ua.astrumon.presentation.scheduler.ReleaseAnnouncer
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ReleaseAnnouncerTest {
    private val releaseNotesService: ReleaseNotesService = mockk()
    private val botMetaService: BotMetaService = mockk()
    private val chatService: ChatService = mockk()
    private val bot: Bot = mockk(relaxed = true)

    private val currentVersion = VersionInfo.VERSION
    private val oldVersion = "0.0.1"
    private val chatIds = listOf(-100L, -200L)

    private val currentNote = ReleaseNote(currentVersion, "2026-01-01", listOf("Some change"))

    @BeforeTest
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `should silently save version on first run when stored is null`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val announcer = ReleaseAnnouncer(releaseNotesService, botMetaService, chatService, scope)

        coEvery { botMetaService.getLastNotifiedVersion() } returns ResultContainer.success(null)
        coEvery { botMetaService.setLastNotifiedVersion(currentVersion) } returns ResultContainer.success(Unit)

        announcer.notifyIfNewVersion(bot)
        testScheduler.runCurrent()

        coVerify { botMetaService.setLastNotifiedVersion(currentVersion) }
        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        scope.cancel()
    }

    @Test
    fun `should do nothing when stored version equals current version`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val announcer = ReleaseAnnouncer(releaseNotesService, botMetaService, chatService, scope)

        coEvery { botMetaService.getLastNotifiedVersion() } returns ResultContainer.success(currentVersion)

        announcer.notifyIfNewVersion(bot)
        testScheduler.runCurrent()

        coVerify(exactly = 0) { botMetaService.setLastNotifiedVersion(any()) }
        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        scope.cancel()
    }

    @Test
    fun `should broadcast to all chats when version changes and note found`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val announcer = ReleaseAnnouncer(releaseNotesService, botMetaService, chatService, scope)

        coEvery { botMetaService.getLastNotifiedVersion() } returns ResultContainer.success(oldVersion)
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(listOf(currentNote))
        coEvery { chatService.getAnnouncementChatIds() } returns ResultContainer.success(chatIds)
        coEvery { botMetaService.setLastNotifiedVersion(currentVersion) } returns ResultContainer.success(Unit)

        announcer.notifyIfNewVersion(bot)
        testScheduler.runCurrent()

        coVerify { bot.sendMessage(chatId = ChatId.fromId(-100L), text = any(), parseMode = ParseMode.HTML) }
        coVerify { bot.sendMessage(chatId = ChatId.fromId(-200L), text = any(), parseMode = ParseMode.HTML) }
        coVerify { botMetaService.setLastNotifiedVersion(currentVersion) }
        scope.cancel()
    }

    @Test
    fun `should save version without broadcast when no release note found`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val announcer = ReleaseAnnouncer(releaseNotesService, botMetaService, chatService, scope)

        coEvery { botMetaService.getLastNotifiedVersion() } returns ResultContainer.success(oldVersion)
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(
            listOf(ReleaseNote("9.9.9", "2099-01-01", listOf("future"))),
        )
        coEvery { botMetaService.setLastNotifiedVersion(currentVersion) } returns ResultContainer.success(Unit)

        announcer.notifyIfNewVersion(bot)
        testScheduler.runCurrent()

        coVerify { botMetaService.setLastNotifiedVersion(currentVersion) }
        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        scope.cancel()
    }

    @Test
    fun `should continue to other chats when one sendMessage throws`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val announcer = ReleaseAnnouncer(releaseNotesService, botMetaService, chatService, scope)

        coEvery { botMetaService.getLastNotifiedVersion() } returns ResultContainer.success(oldVersion)
        coEvery { releaseNotesService.getAll() } returns ResultContainer.success(listOf(currentNote))
        coEvery { chatService.getAnnouncementChatIds() } returns ResultContainer.success(chatIds)
        coEvery { botMetaService.setLastNotifiedVersion(currentVersion) } returns ResultContainer.success(Unit)
        every {
            bot.sendMessage(chatId = ChatId.fromId(-100L), text = any(), parseMode = ParseMode.HTML)
        } throws RuntimeException("Telegram error")

        announcer.notifyIfNewVersion(bot)
        testScheduler.runCurrent()

        coVerify { bot.sendMessage(chatId = ChatId.fromId(-200L), text = any(), parseMode = ParseMode.HTML) }
        coVerify { botMetaService.setLastNotifiedVersion(currentVersion) }
        scope.cancel()
    }

    @Test
    fun `should advance version pointer without broadcast when getAll fails`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val announcer = ReleaseAnnouncer(releaseNotesService, botMetaService, chatService, scope)

        coEvery { botMetaService.getLastNotifiedVersion() } returns ResultContainer.success(oldVersion)
        coEvery { releaseNotesService.getAll() } returns
            ResultContainer.failure(DatabaseException("read error"))
        coEvery { botMetaService.setLastNotifiedVersion(currentVersion) } returns ResultContainer.success(Unit)

        announcer.notifyIfNewVersion(bot)
        testScheduler.runCurrent()

        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
        coVerify { botMetaService.setLastNotifiedVersion(currentVersion) }
        scope.cancel()
    }
}
