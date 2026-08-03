package com.ua.astrumon.presentation.util

import com.ua.astrumon.common.util.escapeHtml
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
