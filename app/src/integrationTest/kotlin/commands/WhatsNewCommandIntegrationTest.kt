package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.data.bot.releasenotes.ReleaseNotesRepositoryImpl
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.commands.WhatsNewCommand
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.util.ReleaseNotesFormatter
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class WhatsNewCommandIntegrationTest : BaseIntegrationTest() {
    private val releaseNotesService = ReleaseNotesService(ReleaseNotesRepositoryImpl())
    private lateinit var whatsNewCommand: WhatsNewCommand

    @BeforeTest
    fun setupWhatsNew() {
        val whatsNewController = WhatsNewController(releaseNotesService, chatService, memberService, messagesProvider)
        whatsNewCommand = WhatsNewCommand(whatsNewController, messagesProvider)
    }

    @Test
    fun `whatsnew reply reflects the latest entry`() = runTest {
        // Derive the expectation from the same source the controller reads, so the assertion never
        // goes stale on a version bump. An internal-only latest entry (empty changes) yields no reply
        // (spovishun-134); a normal entry yields its formatted text behind the command's prefix —
        // the command, not the controller, assembles the final text.
        val notes = releaseNotesService.getAll(BotLanguage.UK).getOrThrow()
        val prefix = messagesProvider.forChat(testChatId).whatsNew.prefix
        val expected = ReleaseNotesFormatter.formatLatest(notes)?.let { prefix + it }
        val update = buildUpdate("/whatsnew")

        whatsNewCommand.execute(bot, update)

        if (expected == null) {
            verify(exactly = 0) { bot.sendMessage(ChatId.fromId(testChatId), any<String>(), ParseMode.HTML) }
        } else {
            verify { bot.sendMessage(ChatId.fromId(testChatId), match { it == expected }, ParseMode.HTML) }
        }
    }

    @Test
    fun `whatsnew dollar-h should reply with full history`() = runTest {
        val update = buildUpdate("/whatsnew \$h")

        whatsNewCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("1.4.0") && it.contains("1.0.0") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `whatsnew reply is rendered in the language stored for the chat`() = runTest {
        // Asserted on the history rather than the latest entry: the latest may be internal-only and
        // reply with nothing, while the history always renders every translated record.
        chatService.ensureChat(testChatId, "Test chat", "supergroup").getOrThrow()
        chatService.setLanguage(testChatId, BotLanguage.EN).getOrThrow()
        val notes = releaseNotesService.getAll(BotLanguage.EN).getOrThrow()
        val expected = ReleaseNotesFormatter.formatHistory(BotMessages.of(BotLanguage.EN), notes)
        val update = buildUpdate("/whatsnew \$h")

        whatsNewCommand.execute(bot, update)

        verify {
            bot.sendMessage(ChatId.fromId(testChatId), match { it.contains(requireNotNull(expected)) }, ParseMode.HTML)
        }
    }

    @Test
    fun `whatsnew dollar-h should include change bullets`() = runTest {
        // The latest entry may be internal-only (no bullets); history always renders the non-empty
        // entries, so bullet rendering is asserted against the full history.
        val update = buildUpdate("/whatsnew \$h")

        whatsNewCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("•") },
                ParseMode.HTML,
            )
        }
    }
}
