package com.ua.astrumon.presentation.scheduler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.ReleaseNote
import com.ua.astrumon.domain.bot.service.BotMetaService
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
import com.ua.astrumon.presentation.util.ReleaseNotesFormatter
import com.ua.astrumon.presentation.util.withChatLogContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ReleaseAnnouncer(
    private val releaseNotesService: ReleaseNotesService,
    private val botMetaService: BotMetaService,
    private val chatService: ChatService,
    private val scope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(ReleaseAnnouncer::class.java)

    fun notifyIfNewVersion(bot: Bot) {
        scope.launch { runCheck(bot) }
    }

    private suspend fun runCheck(bot: Bot) {
        val current = VersionInfo.VERSION
        val stored = botMetaService.getLastNotifiedVersion().getOrNull()
        when {
            stored == null -> botMetaService.setLastNotifiedVersion(current)
            stored == current -> return
            else -> broadcastNewVersion(bot, current)
        }
    }

    private suspend fun broadcastNewVersion(
        bot: Bot,
        version: String,
    ) {
        // Every language is rendered up front, from one read, so the "is there anything to announce"
        // gate and the fan-out below can never disagree about what this version says.
        // A single null is a language deliberately left out — an empty `changes` list suppresses just
        // that one (spovishun-152). All-null is the missing or internal-only release: mark it as
        // notified and skip, so we neither spam chats nor re-evaluate it on every restart (spovishun-134).
        val textByLanguage = BotLanguage.entries.associateWith { renderNotes(version, it) }
        if (textByLanguage.values.all { it == null }) {
            logger.warn("Nothing to broadcast for $version — no release_notes entry, or no language renders one")
            botMetaService.setLastNotifiedVersion(version)
            return
        }
        val chats = chatService.getAnnouncementChats().getOrNull() ?: return
        chats.groupBy { it.language }.forEach { (language, group) ->
            val text = textByLanguage[language] ?: return@forEach
            sendToAllChats(bot, group.map { it.chatId }, text)
        }
        botMetaService.setLastNotifiedVersion(version)
    }

    private suspend fun renderNotes(
        version: String,
        language: BotLanguage,
    ): String? = findReleaseNote(version, language)?.let { ReleaseNotesFormatter.formatLatest(listOf(it)) }

    private suspend fun findReleaseNote(
        version: String,
        language: BotLanguage,
    ): ReleaseNote? {
        val notes = releaseNotesService.getAll(language).getOrNull() ?: return null
        return notes.firstOrNull { it.version == version }
    }

    /** Each send logs against the chat it targets, so a partial broadcast names its casualties. */
    private suspend fun sendToAllChats(
        bot: Bot,
        chatIds: List<Long>,
        text: String,
    ) {
        chatIds.forEach { id ->
            withChatLogContext(id) {
                try {
                    bot.sendMessage(ChatId.fromId(id), text, parseMode = ParseMode.HTML)
                } catch (e: CancellationException) {
                    // Defensive, unlike the two guards in [BirthdayGreetingScheduler]: the send is a
                    // blocking call, so nothing inside this try can currently raise cancellation —
                    // `withChatLogContext` is the suspension point and it sits outside. The guard
                    // exists so that adding a suspending call here does not silently reintroduce
                    // spovishun-190.
                    throw e
                } catch (e: Exception) {
                    logger.warn("Failed to broadcast release notes: ${e.message}")
                }
            }
        }
    }
}
