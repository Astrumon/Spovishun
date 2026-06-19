package com.ua.astrumon.domain.config

/**
 * Access-control contract the bot relies on: which chats it is allowed to serve.
 * Declared in :domain as a port; the app layer ([AppConfig]) implements it so the :bot module
 * never depends on :data (where the rest of the app configuration lives).
 */
interface ChatAccessConfig {
    val allowedChatIds: Set<Long>
}
