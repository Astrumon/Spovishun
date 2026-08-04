package com.ua.astrumon.presentation.util

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
}

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
 * Nested calls inherit the surrounding map and override only the two chat keys.
 */
suspend fun <T> withChatLogContext(
    chatId: Long?,
    chatType: String? = null,
    block: suspend () -> T,
): T = withContext(MDCContext(chatContextMap(chatId, chatType))) { block() }

/**
 * Snapshot of the current chat log context, to hand to a coroutine launched on a long-lived scope.
 *
 * `scope.launch {}` takes its context from the scope, not from the caller, so work deferred onto an
 * injected scope would otherwise log as `system`. Passing this preserves the chat that started it.
 */
fun chatLogContextSnapshot(): CoroutineContext = MDCContext()

private fun chatContextMap(
    chatId: Long?,
    chatType: String?,
): Map<String, String> = MDC
    .getCopyOfContextMap()
    .orEmpty()
    .toMutableMap()
    .apply {
        setOrRemove(ChatLogContext.CHAT_ID, chatId?.toString())
        setOrRemove(ChatLogContext.CHAT_TYPE, chatType)
    }

private fun MutableMap<String, String>.setOrRemove(
    key: String,
    value: String?,
) {
    if (value == null) remove(key) else put(key, value)
}
