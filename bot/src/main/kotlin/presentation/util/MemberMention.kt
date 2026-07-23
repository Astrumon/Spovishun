package com.ua.astrumon.presentation.util

import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.Member

/**
 * Renders a Telegram mention for a member (ParseMode.HTML).
 *
 * Members without a real Telegram username are stored with the synthetic value
 * `user_{userId}` (see command registration flow) — for them, and for blank
 * usernames, an inline `tg://user?id` mention is used so the greeting still tags
 * the user.
 */
fun Member.toHtmlMention(): String = if (username.isNotBlank() && username != "user_$userId") {
    "@$username"
} else {
    "<a href=\"tg://user?id=$userId\">${firstName.escapeHtml()}</a>"
}
