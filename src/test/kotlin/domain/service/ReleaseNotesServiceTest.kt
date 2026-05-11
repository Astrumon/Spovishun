package domain.service

import com.ua.astrumon.domain.service.ReleaseNotesService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleaseNotesServiceTest {

    private val service = ReleaseNotesService()

    @Test
    fun `getAll should return non-empty list`() = runTest {
        val result = service.getAll()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isNotEmpty())
    }

    @Test
    fun `getAll should parse version field`() = runTest {
        val notes = service.getAll().getOrThrow()

        assertTrue(notes.all { it.version.isNotBlank() })
    }

    @Test
    fun `getAll should parse date field`() = runTest {
        val notes = service.getAll().getOrThrow()

        assertTrue(notes.all { it.date.isNotBlank() })
    }

    @Test
    fun `getAll should parse changes list`() = runTest {
        val notes = service.getAll().getOrThrow()

        assertTrue(notes.all { it.changes.isNotEmpty() })
    }

    @Test
    fun `getAll should return notes in newest-first order`() = runTest {
        val notes = service.getAll().getOrThrow()

        val versions = notes.map { it.version }
        val sorted = versions.sortedDescending()
        assertTrue(versions == sorted)
    }

    @Test
    fun `getAll should contain entry for current release`() = runTest {
        val notes = service.getAll().getOrThrow()

        assertNotNull(notes.firstOrNull { it.version == "1.4.0" })
    }
}
