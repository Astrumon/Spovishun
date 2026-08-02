package data.releasenotes

import com.ua.astrumon.data.bot.releasenotes.ReleaseNoteDto
import com.ua.astrumon.domain.bot.model.BotLanguage
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract tests over the raw `release_notes.json` keys rather than over the repository output.
 *
 * Once a record is mapped, a typo'd language key is indistinguishable from a translation nobody
 * wrote — both resolve through the Ukrainian fallback — and an omitted language is
 * indistinguishable from a deliberately empty one. The guard therefore has to read the keys.
 */
class ReleaseNotesResourceTest {
    private val supportedCodes = BotLanguage.entries.map { it.code }.toSet()

    private val records: List<ReleaseNoteDto> = Json { ignoreUnknownKeys = true }
        .decodeFromString(
            checkNotNull(javaClass.classLoader.getResourceAsStream(RELEASE_NOTES_RESOURCE)) {
                "$RELEASE_NOTES_RESOURCE is missing from the test classpath"
            }.use { it.readBytes().decodeToString() },
        )

    @Test
    fun `every changes key should be a supported language code`() {
        val unknown = records
            .flatMap { record -> record.changes.keys.map { record.version to it } }
            .filterNot { (_, code) -> code in supportedCodes }

        assertTrue(unknown.isEmpty(), "Unknown language codes in release_notes.json: $unknown")
    }

    @Test
    fun `a record that announces anything should list every supported language`() {
        // An empty list means "say nothing in this language" and is legal; leaving the key out is
        // not — that silently borrows the Ukrainian text instead of announcing the deliberate choice.
        val incomplete = records
            .filter { it.changes.isNotEmpty() }
            .filterNot { it.changes.keys == supportedCodes }
            .map { it.version to it.changes.keys }

        assertTrue(incomplete.isEmpty(), "Release notes missing a supported language: $incomplete")
    }

    @Test
    fun `an internal-only record should carry no language at all`() {
        // The all-languages suppression is an empty object, so a record can never be half-suppressed.
        val internalOnly = records.filter { record -> record.changes.values.all { it.isEmpty() } }

        assertEquals(
            internalOnly.filter { it.changes.isEmpty() },
            internalOnly,
            "An internal-only release must use \"changes\": {}, not per-language empty lists",
        )
    }

    private companion object {
        const val RELEASE_NOTES_RESOURCE = "release_notes.json"
    }
}
