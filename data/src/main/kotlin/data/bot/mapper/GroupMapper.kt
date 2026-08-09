package com.ua.astrumon.data.bot.mapper

import com.ua.astrumon.data.bot.table.GroupSettings
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.model.PingMark
import org.jetbrains.exposed.sql.ResultRow

/**
 * Reads a `groups` row joined with its optional `group_settings` row (spovishun-32).
 *
 * The join is a LEFT JOIN, so a group without a settings row yields nulls — those fall back to the
 * defaults the table itself declares.
 */
fun ResultRow.toGroup() = Group(
    id = this[Groups.id].value,
    chatId = this[Groups.chatId],
    name = this[Groups.name],
    memberUsernames = emptyList(),
    readinessEnabled = this.getOrNull(GroupSettings.readinessEnabled) ?: true,
    icon = this.getOrNull(GroupSettings.icon),
    pingMark = this.toPingMark(),
)

/**
 * Two columns, three states (spovishun-180). The flag is checked first: a hidden mark keeps whatever
 * emoji it was hiding, so the column being set says nothing about whether it is rendered.
 */
private fun ResultRow.toPingMark(): PingMark = when {
    this.getOrNull(GroupSettings.pingMarkEnabled) == false -> PingMark.Hidden
    else -> this.getOrNull(GroupSettings.pingMark)?.let(PingMark::Custom) ?: PingMark.Default
}
