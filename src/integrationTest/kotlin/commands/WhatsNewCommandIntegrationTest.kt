package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.service.ReleaseNotesService
import com.ua.astrumon.presentation.bot.commands.WhatsNewCommand
import com.ua.astrumon.presentation.controller.WhatsNewController
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class WhatsNewCommandIntegrationTest : BaseIntegrationTest() {

    private val releaseNotesService = ReleaseNotesService()
    private lateinit var whatsNewCommand: WhatsNewCommand

    @BeforeTest
    fun setupWhatsNew() {
        val whatsNewController = WhatsNewController(releaseNotesService, chatService, memberService)
        whatsNewCommand = WhatsNewCommand(whatsNewController)
    }

    @Test
    fun `whatsnew should reply with latest version entry`() = runTest {
        val update = buildUpdate("/whatsnew")

        whatsNewCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("1.4.0") },
                ParseMode.HTML
            )
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
                ParseMode.HTML
            )
        }
    }

    @Test
    fun `whatsnew should include change description in reply`() = runTest {
        val update = buildUpdate("/whatsnew")

        whatsNewCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("•") },
                ParseMode.HTML
            )
        }
    }
}
