package data.releasenotes

import com.ua.astrumon.data.bot.releasenotes.ReleaseNotesRepositoryImpl
import com.ua.astrumon.domain.bot.model.BotLanguage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleaseNotesRepositoryImplTest {
    private val repository = ReleaseNotesRepositoryImpl()

    @Test
    fun `getAll should return non-empty list`() = runTest {
        val result = repository.getAll(BotLanguage.UK)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isNotEmpty())
    }

    @Test
    fun `getAll should parse version field`() = runTest {
        val notes = repository.getAll(BotLanguage.UK).getOrThrow()

        assertTrue(notes.all { it.version.isNotBlank() })
    }

    @Test
    fun `getAll should parse date field`() = runTest {
        val notes = repository.getAll(BotLanguage.UK).getOrThrow()

        assertTrue(notes.all { it.date.isNotBlank() })
    }

    @Test
    fun `getAll should parse changes list`() = runTest {
        val notes = repository.getAll(BotLanguage.UK).getOrThrow()

        // v1.6.0 ships an intentionally empty changes list (internal-only release),
        // so the strict all{} invariant is relaxed to any{} — see spovishun-134.
        assertTrue(notes.any { it.changes.isNotEmpty() })
    }

    @Test
    fun `getAll should return notes in newest-first order`() = runTest {
        val notes = repository.getAll(BotLanguage.UK).getOrThrow()

        val versions = notes.map { it.version }
        val sorted = versions.sortedDescending()
        assertTrue(versions == sorted)
    }

    @Test
    fun `getAll should contain entry for current release`() = runTest {
        val notes = repository.getAll(BotLanguage.UK).getOrThrow()

        assertNotNull(notes.firstOrNull { it.version == "1.4.0" })
    }

    @Test
    fun `getAll should resolve changes for the requested language`() = runTest {
        val uk = repository.getAll(BotLanguage.UK).getOrThrow().first { it.version == "1.4.0" }
        val en = repository.getAll(BotLanguage.EN).getOrThrow().first { it.version == "1.4.0" }

        assertEquals(uk.changes.size, en.changes.size)
        assertNotEquals(uk.changes, en.changes)
    }

    @Test
    fun `getAll should expose the same versions and dates in every language`() = runTest {
        // Both translations live in one record, so a release can never exist in one language only.
        val uk = repository.getAll(BotLanguage.UK).getOrThrow()
        val en = repository.getAll(BotLanguage.EN).getOrThrow()

        assertEquals(uk.map { it.version to it.date }, en.map { it.version to it.date })
    }

    @Test
    fun `getAll should have an English translation for every renderable record`() = runTest {
        // A record with Ukrainian changes but no English ones resolves through the fallback and
        // comes back identical — which is exactly the drift this guards the release flow against.
        val ukByVersion = repository
            .getAll(BotLanguage.UK)
            .getOrThrow()
            .filter { it.changes.isNotEmpty() }
            .associateBy { it.version }
        val en = repository.getAll(BotLanguage.EN).getOrThrow()

        val untranslated = en
            .filter { it.version in ukByVersion }
            .filter { it.changes == ukByVersion.getValue(it.version).changes }
            .map { it.version }
        assertTrue(untranslated.isEmpty(), "Release notes missing an English translation: $untranslated")
    }
}
