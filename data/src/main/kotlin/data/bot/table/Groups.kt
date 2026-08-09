package com.ua.astrumon.data.bot.table

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

object Groups : LongIdTable("groups") {
    val chatId = reference("chat_id", Chats.chatId)
    val name = varchar("name", 64)

    init {
        uniqueIndex(chatId, name)
    }

    /**
     * Canonical resolve of a group by the key a user types. The unique index above is what makes
     * `(chat_id, name)` a key; this is the single place its comparison semantics live, so changing
     * them — to `eqIgnoreCase`, say, as usernames already are — is one edit rather than a hunt
     * through every repository.
     */
    internal fun keyed(
        chatId: Long,
        key: String,
    ): Op<Boolean> = (Groups.chatId eq chatId) and (Groups.name eq key)
}

/** The row behind [Groups.keyed], for callers that need the group itself and not just the predicate. */
internal fun Groups.rowFor(
    chatId: Long,
    key: String,
): ResultRow? = selectAll().where(keyed(chatId, key)).singleOrNull()
