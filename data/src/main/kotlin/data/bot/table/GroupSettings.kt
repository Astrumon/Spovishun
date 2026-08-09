package com.ua.astrumon.data.bot.table

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * Per-group parameters editable through `/editg` (spovishun-32).
 *
 * One row per group at most — the primary key is the foreign key, so the table is a 1:1 extension of
 * [Groups] rather than an entity of its own. There is deliberately no `chat_id` here: a group
 * already carries one, and every settings write resolves its group by `(chat_id, name)` first.
 */
object GroupSettings : Table("group_settings") {
    val groupId = reference("group_id", Groups, onDelete = ReferenceOption.CASCADE)
    val icon = varchar("icon", EMOJI_MAX_LENGTH).nullable()
    val readinessEnabled = bool("readiness_enabled").default(true)

    /**
     * The emoji `/ping` repeats once per member (spovishun-180). Null means "use the locale default";
     * [pingMarkEnabled] off means "render nothing", and deliberately leaves this column alone so the
     * chosen emoji survives being hidden.
     */
    val pingMark = varchar("ping_mark", EMOJI_MAX_LENGTH).nullable()
    val pingMarkEnabled = bool("ping_mark_enabled").default(true)

    override val primaryKey = PrimaryKey(groupId)

    /** Room for a multi-codepoint emoji sequence (ZWJ family, skin-tone modifier, variation selector). */
    const val EMOJI_MAX_LENGTH = 32
}
