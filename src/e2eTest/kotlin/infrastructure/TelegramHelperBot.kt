package infrastructure

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TelegramHelperBot(
    private val token: String,
    private val chatId: Long
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }
    private val baseUrl = "https://api.telegram.org/bot$token"
    private var updateOffset: Long = 0

    @Serializable
    data class TgResponse<T>(val ok: Boolean, val result: T? = null)

    @Serializable
    data class TgUpdate(val update_id: Long, val message: TgMessage? = null)

    @Serializable
    data class TgMessage(
        val message_id: Long,
        val from: TgUser? = null,
        val chat: TgChat,
        val text: String? = null,
        val date: Long = 0L
    )

    @Serializable
    data class TgUser(
        val id: Long,
        val is_bot: Boolean = false,
        val first_name: String = "",
        val username: String? = null
    )

    @Serializable
    data class TgChat(val id: Long, val type: String = "")

    @Serializable
    data class SendMessageRequest(val chat_id: Long, val text: String)

    /** Clear all pending updates so tests start with a clean update stream. */
    suspend fun clearPendingUpdates() {
        val response = client.get("$baseUrl/getUpdates") {
            parameter("offset", -1)
            parameter("limit", 1)
        }.body<TgResponse<List<TgUpdate>>>()
        val lastUpdate = response.result?.lastOrNull()
        updateOffset = if (lastUpdate != null) lastUpdate.update_id + 1 else 0
    }

    /** Send a text message (or command) to the test chat as the helper bot. */
    suspend fun sendCommand(text: String): TgMessage {
        val response = client.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(chat_id = chatId, text = text))
        }.body<TgResponse<TgMessage>>()
        return response.result ?: error("Failed to send message: $text")
    }

    /**
     * Poll getUpdates until a message from [fromBotId] in the test chat
     * matches [predicate], or until [timeoutSeconds] elapses.
     * Returns the matched message text.
     */
    suspend fun waitForBotResponse(
        fromBotId: Long,
        timeoutSeconds: Int = 15,
        predicate: (String) -> Boolean = { true }
    ): String {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            val response = client.get("$baseUrl/getUpdates") {
                parameter("offset", updateOffset)
                parameter("timeout", 3)
                parameter("allowed_updates", "[\"message\"]")
            }.body<TgResponse<List<TgUpdate>>>()

            for (update in response.result.orEmpty()) {
                updateOffset = update.update_id + 1
                val msg = update.message ?: continue
                if (msg.chat.id != chatId) continue
                val from = msg.from ?: continue
                if (from.id != fromBotId) continue
                if (!from.is_bot) continue
                val text = msg.text ?: continue
                if (predicate(text)) return text
            }
            delay(500)
        }
        error("Timed out after ${timeoutSeconds}s waiting for response from bot $fromBotId")
    }

    /** Get the Telegram user ID of any bot by its token. */
    suspend fun getBotId(botToken: String): Long {
        val tempClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
        }
        return try {
            val response = tempClient.get("https://api.telegram.org/bot$botToken/getMe")
                .body<TgResponse<TgUser>>()
            response.result?.id ?: error("Failed to get bot ID for token")
        } finally {
            tempClient.close()
        }
    }

    /** Returns raw JSON of all recent updates (no filtering) — for diagnostics only. */
    suspend fun getAllUpdatesRaw(): String {
        return client.get("$baseUrl/getUpdates") {
            parameter("offset", updateOffset)
            parameter("timeout", 5)
        }.bodyAsText()
    }

    fun close() {
        client.close()
    }
}
