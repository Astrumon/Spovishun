package com.ua.astrumon.presentation.util

import com.github.kotlintelegrambot.entities.Chat
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import kotlin.coroutines.CoroutineContext

/**
 * MDC keys carrying the chat an update originated from (spovishun-168).
 *
 * A key that is absent renders as `system` — see the encoder pattern in
 * `app/src/main/resources/logback.xml`. That is how a line with no originating chat (startup,
 * shutdown, a scheduler between per-chat passes) marks itself, so no separate "system context"
 * API is needed.
 */
object ChatLogContext {
    const val CHAT_ID = "chatId"
    const val CHAT_TYPE = "chatType"
    const val CHAT_TITLE = "chatTitle"
}

/**
 * Longest chat title the log line carries. Telegram allows 255 characters; a title that long would
 * push the message itself off the readable part of the line in the Admin live-log view.
 */
private const val MAX_CHAT_TITLE_LENGTH = 64

/** Characters that would break the `[chat=…]` field apart — the brackets and the line separators. */
private val UNSAFE_TITLE_CHARS = Regex("""[\[\]\r\n]""")

/**
 * Runs [block] with the originating [chat] attached to every log line it — or anything it calls —
 * emits.
 *
 * This is the overload call sites holding a Telegram [Chat] should use: it owns the single
 * definition of what "the chat's name" is and the sanitizing that keeps the rendered field parseable.
 */
suspend fun <T> withChatLogContext(
    chat: Chat?,
    block: suspend () -> T,
): T = withChatLogContext(chat?.id, chat?.type, chat?.logTitle(), block)

/**
 * Runs [block] with the originating chat attached to every log line it — or anything it calls —
 * emits.
 *
 * The context rides on [MDCContext] rather than a bare `MDC.put`, which matters for two reasons.
 * The MDC is a thread-local, so a plain put is lost the moment `safeDbQuery` hops to
 * `Dispatchers.IO`; as a coroutine context element it is reinstalled on every thread the coroutine
 * touches. And because the map is passed explicitly instead of mutating the ambient MDC, the
 * coroutines machinery owns both halves — nothing can leak onto a `Dispatchers.Default` thread the
 * block started on but did not finish on, on completion, cancellation or failure alike.
 *
 * Nested calls inherit the surrounding map and override only the three chat keys.
 *
 * [chatTitle] is written verbatim: a caller that has a [Chat] should take the overload above rather
 * than sanitize by hand.
 */
suspend fun <T> withChatLogContext(
    chatId: Long?,
    chatType: String? = null,
    chatTitle: String? = null,
    block: suspend () -> T,
): T = withContext(MDCContext(chatContextMap(chatId, chatType, chatTitle))) { block() }

/**
 * Snapshot of the current chat log context, to hand to a coroutine launched on a long-lived scope.
 *
 * `scope.launch {}` takes its context from the scope, not from the caller, so work deferred onto an
 * injected scope would otherwise log as `system`. Passing this preserves the chat that started it.
 */
fun chatLogContextSnapshot(): CoroutineContext = MDCContext()

/**
 * The chat's display name for the log line — a group's `title`, and nothing else.
 *
 * Deliberately does NOT fall back to `username` or `firstName` for a private chat: there the chat is
 * one person, so either field names them outright, and `security.md` allows only anonymized
 * identifiers in a log line. `chatId` already correlates the lines of one private conversation. With
 * no title the key stays out of the MDC and the encoder renders `system`.
 */
private fun Chat.logTitle(): String? = title?.let(::sanitizeChatTitle)?.takeIf { it.isNotEmpty() }

/**
 * Makes a title safe for the `[chat=…]` field the Admin client splits out with `\[chat=([^\]]*)\]`.
 *
 * A bracket in the title would end the field early and a newline would break the line in two, so a
 * chat could corrupt the log format just by renaming itself — the same class of defect this field
 * was added to fix.
 *
 * `take` counts UTF-16 code units, so a cut landing inside a surrogate pair would emit a lone
 * surrogate — and emoji in chat titles are common. The dangling high surrogate is dropped rather
 * than written out as a replacement character.
 */
private fun sanitizeChatTitle(raw: String): String = raw
    .replace(UNSAFE_TITLE_CHARS, " ")
    .take(MAX_CHAT_TITLE_LENGTH)
    .trimEnd { it.isHighSurrogate() }
    .trim()

private fun chatContextMap(
    chatId: Long?,
    chatType: String?,
    chatTitle: String?,
): Map<String, String> = MDC
    .getCopyOfContextMap()
    .orEmpty()
    .toMutableMap()
    .apply {
        setOrRemove(ChatLogContext.CHAT_ID, chatId?.toString())
        setOrRemove(ChatLogContext.CHAT_TYPE, chatType)
        setOrRemove(ChatLogContext.CHAT_TITLE, chatTitle)
    }

private fun MutableMap<String, String>.setOrRemove(
    key: String,
    value: String?,
) {
    if (value == null) remove(key) else put(key, value)
}
