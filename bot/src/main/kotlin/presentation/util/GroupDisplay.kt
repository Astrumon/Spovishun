package com.ua.astrumon.presentation.util

import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.PingMark
import com.ua.astrumon.domain.bot.service.GroupWithMembers

/**
 * How a group is named to users once it may carry an icon (spovishun-32).
 *
 * Every place that shows a group name goes through one of these, so setting `$icon` cannot leave the
 * icon visible in `/ping` but missing from `/groups` or the inline picker.
 */
fun GroupWithMembers.displayLabel(): String = icon?.let { "$it $name" } ?: name

/**
 * HTML-message counterpart of [displayLabel].
 *
 * The icon is escaped too. `EmojiValidator` already rejects every code point that is not part of an
 * emoji, so nothing dangerous can reach the column through `/editg` — but this is an HTML sink, and
 * it should not depend on a validator two modules away staying airtight.
 */
fun GroupWithMembers.displayLabelHtml(): String {
    val escapedName = name.escapeHtml()
    return icon?.let { "${it.escapeHtml()} $escapedName" } ?: escapedName
}

/**
 * The emoji strip `/ping` puts after a group's name, one per member (spovishun-180).
 *
 * The three [PingMark] states resolve here and nowhere else, for the same reason [displayLabel]
 * exists: a setting that decides how a group is rendered must have one place that reads it, or the
 * next surface to render a group will quietly disagree with this one.
 *
 * [default] is the emoji the chat's language declares — passed in rather than looked up, because
 * this module's display helpers do not reach for `BotMessages` on their own.
 */
fun GroupWithMembers.pingMarks(
    count: Int,
    default: String,
): String = when (val mark = pingMark) {
    PingMark.Hidden -> ""
    PingMark.Default -> default.escapeHtml().repeat(count)
    is PingMark.Custom -> mark.emoji.escapeHtml().repeat(count)
}
