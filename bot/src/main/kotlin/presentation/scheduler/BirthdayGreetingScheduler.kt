package com.ua.astrumon.presentation.scheduler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.util.toHtmlMention
import com.ua.astrumon.presentation.util.withChatLogContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class BirthdayGreetingScheduler(
    private val birthdayService: BirthdayService,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val messagesProvider: BotMessagesProvider,
) {
    private val logger = LoggerFactory.getLogger(BirthdayGreetingScheduler::class.java)
    private val zone: ZoneId = ZoneId.of("Europe/Kyiv")
    private val targetHour = 12

    fun start(bot: Bot) {
        scope.launch {
            val today = LocalDate.now(clock.withZone(zone))
            runJobIfNotYetSent(bot, today)
            while (isActive) {
                val nextRun = nextRunInstant()
                delay(Duration.between(clock.instant(), nextRun).toMillis())
                val runDate = LocalDate.now(clock.withZone(zone))
                runJobIfNotYetSent(bot, runDate)
            }
        }
    }

    private suspend fun runJobIfNotYetSent(
        bot: Bot,
        today: LocalDate,
    ) {
        val birthDates = mutableListOf(BirthDate(today.dayOfMonth, today.monthValue))
        if (today.monthValue == 2 && today.dayOfMonth == 28 && !today.isLeapYear) {
            birthDates.add(BirthDate(29, 2))
        }
        birthDates.forEach { greetBirthday(bot, it, today.year) }
    }

    private suspend fun greetBirthday(
        bot: Bot,
        birthday: BirthDate,
        year: Int,
    ) {
        val members = birthdayService.getMembersWithBirthday(birthday).fold(
            onSuccess = { it },
            onFailure = { emptyList() },
        )
        for (member in members) {
            try {
                val alreadyGreeted = birthdayService
                    .wasGreetedThisYear(member.id, year)
                    .fold(onSuccess = { it }, onFailure = { true })
                if (alreadyGreeted) continue

                val chatIds = birthdayService
                    .findChatIdsForMember(member.id)
                    .fold(onSuccess = { it }, onFailure = { emptyList() })
                if (chatIds.isEmpty()) continue

                // Unchanged semantics: a member is only marked greeted once every chat got the
                // message, so a partial failure is retried rather than silently swallowed.
                if (!sendGreetings(bot, member, chatIds)) continue

                val sentAt = Instant.fromEpochMilliseconds(clock.instant().toEpochMilli())
                birthdayService.recordGreetingSent(member.id, year, sentAt)
            } catch (e: Exception) {
                // Reached only by the lookups and the record step, which span every chat the member
                // is in — this pass-level failure has no one chat to name and logs as `system`.
                // Sends are attributed in [sendGreeting]. No member id here (security.md).
                logger.error("Birthday greeting pass failed for one member", e)
            }
        }
    }

    /** @return whether every chat got the greeting. */
    private suspend fun sendGreetings(
        bot: Bot,
        member: Member,
        chatIds: List<Long>,
    ): Boolean = chatIds
        .map { chatId -> sendGreeting(bot, member, chatId) }
        .all { it }

    /**
     * Rendered and sent per chat: one member's greeting can land in chats on different languages,
     * and both the send and any failure log against the chat they target (spovishun-168).
     */
    private suspend fun sendGreeting(
        bot: Bot,
        member: Member,
        chatId: Long,
    ): Boolean = withChatLogContext(chatId) {
        try {
            val text = messagesProvider.forChat(chatId).birthday.randomGreeting(member.toHtmlMention())
            bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
            true
        } catch (e: Exception) {
            logger.error("Failed to send birthday greeting", e)
            false
        }
    }

    private fun nextRunInstant(): java.time.Instant {
        val nowKyiv = ZonedDateTime.ofInstant(clock.instant(), zone)
        val today12 = nowKyiv.toLocalDate().atTime(targetHour, 0).atZone(zone)
        val target = if (nowKyiv.isBefore(today12)) today12 else today12.plusDays(1)
        return target.toInstant()
    }
}
