package com.ua.astrumon.domain.bot.model

import java.util.Locale

/**
 * The language a chat receives bot replies in.
 *
 * [UK] maps to [Locale.ROOT] on purpose: the Ukrainian copy lives in the **base** bundle
 * (`messages.properties`). Mapping it to `Locale("uk")` would make `ResourceBundle.getBundle`
 * miss a `messages_uk.properties` and fall through to the JVM default locale — on an
 * English-locale host a Ukrainian chat would silently render English. `Locale.ROOT` resolves
 * straight to the base bundle and keeps the JVM default out of the lookup chain.
 */
enum class BotLanguage(
    val code: String,
    val locale: Locale,
) {
    UK("uk", Locale.ROOT),
    EN("en", Locale.ENGLISH),
    ;

    companion object {
        /** Unknown or absent codes fall back to [UK] — a chat is never left without copy. */
        fun fromCode(code: String?): BotLanguage = entries.firstOrNull { it.code == code } ?: UK
    }
}
