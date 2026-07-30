package infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resolves the helper bot's real Telegram user id — and nothing else.
 *
 * The narrow surface is deliberate. Telegram never delivers a bot's messages to another bot, so a
 * second bot cannot poll `getUpdates` to read back what the bot under test posted. An earlier
 * version of this class carried `sendCommand` / `waitForBotResponse` / `clearPendingUpdates` for
 * exactly that purpose; they had zero call sites because the platform makes them unusable
 * (spovishun-160). Read-back now comes from the send call's own API response instead — see
 * [BaseE2ETest.deliveredMessages].
 *
 * What remains is still genuinely useful: the tests dispatch synthetic updates, and those updates
 * must carry a user id that really exists on Telegram, otherwise `getChatMember` — the one real
 * Telegram lookup the production code performs — has nothing to resolve.
 */
class TelegramHelperBot(
    private val token: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    @Serializable
    private data class TgResponse<T>(
        val ok: Boolean,
        val result: T? = null,
    )

    @Serializable
    private data class TgUser(
        val id: Long,
    )

    /** Telegram user id behind [token], via `getMe`. */
    suspend fun resolveBotId(): Long {
        val response = client
            .get("https://api.telegram.org/bot$token/getMe")
            .body<TgResponse<TgUser>>()
        return response.result?.id ?: error("getMe returned no result for the helper bot token")
    }

    fun close() {
        client.close()
    }
}
