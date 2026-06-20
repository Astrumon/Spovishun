package com.ua.astrumon.domain.cache

import com.ua.astrumon.domain.model.MemberWithChat
import java.util.concurrent.ConcurrentHashMap

class UserCache {
    private val cache = ConcurrentHashMap<String, MemberWithChat>()

    private fun key(
        chatId: Long,
        username: String,
    ) = "$chatId:$username"

    fun get(
        chatId: Long,
        username: String,
    ): MemberWithChat? = cache[key(chatId, username)]

    fun put(
        chatId: Long,
        username: String,
        member: MemberWithChat,
    ) {
        cache[key(chatId, username)] = member
    }

    fun evict(
        chatId: Long,
        username: String,
    ) {
        cache.remove(key(chatId, username))
    }

    fun evictAllInChat(chatId: Long) {
        val prefix = "$chatId:"
        cache.keys.removeAll { it.startsWith(prefix) }
    }
}
