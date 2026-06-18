package com.ua.astrumon.domain.cache

import java.util.concurrent.ConcurrentHashMap

class ChatCache {
    private val known = ConcurrentHashMap.newKeySet<Long>()

    fun isKnown(chatId: Long): Boolean = known.contains(chatId)

    fun markKnown(chatId: Long) {
        known.add(chatId)
    }
}
