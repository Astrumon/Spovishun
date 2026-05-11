package com.ua.astrumon.presentation.scheduler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.model.ReleaseNote
import com.ua.astrumon.domain.service.BotMetaService
import com.ua.astrumon.domain.service.ChatService
import com.ua.astrumon.domain.service.ReleaseNotesService
import com.ua.astrumon.presentation.util.ReleaseNotesFormatter
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

    private suspend fun broadcastNewVersion(bot: Bot, version: String) {
        val entry = findReleaseNote(version) ?: run {
            botMetaService.setLastNotifiedVersion(version)
            return
        }
        val chatIds = chatService.getAllChatIds().getOrNull() ?: return
        val text = ReleaseNotesFormatter.formatLatest(listOf(entry))
        sendToAllChats(bot, chatIds, text)
        botMetaService.setLastNotifiedVersion(version)
    }

    private suspend fun findReleaseNote(version: String): ReleaseNote? {
        val notes = releaseNotesService.getAll().getOrNull() ?: return null
        return notes.firstOrNull { it.version == version }
            .also { if (it == null) logger.warn("No release_notes entry for $version — skipping broadcast") }
    }

    private fun sendToAllChats(bot: Bot, chatIds: List<Long>, text: String) {
        chatIds.forEach { id ->
            try {
                bot.sendMessage(ChatId.fromId(id), text, parseMode = ParseMode.HTML)
            } catch (e: Exception) {
                logger.warn("Failed to broadcast to chat $id: ${e.message}")
            }
        }
    }
}
