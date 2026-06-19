package data.releasenotes

import com.ua.astrumon.data.releasenotes.ReleaseNotesRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleaseNotesRepositoryImplTest {
    private val repository = ReleaseNotesRepositoryImpl()

    @Test
    fun `getAll should return non-empty list`() = runTest {
        val result = repository.getAll()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isNotEmpty())
    }

    @Test
    fun `getAll should parse version field`() = runTest {
        val notes = repository.getAll().getOrThrow()

        assertTrue(notes.all { it.version.isNotBlank() })
    }

    @Test
    fun `getAll should parse date field`() = runTest {
        val notes = repository.getAll().getOrThrow()

        assertTrue(notes.all { it.date.isNotBlank() })
    }

    @Test
    fun `getAll should parse changes list`() = runTest {
        val notes = repository.getAll().getOrThrow()

        assertTrue(notes.all { it.changes.isNotEmpty() })
    }

    @Test
    fun `getAll should return notes in newest-first order`() = runTest {
        val notes = repository.getAll().getOrThrow()

        val versions = notes.map { it.version }
        val sorted = versions.sortedDescending()
        assertTrue(versions == sorted)
    }

    @Test
    fun `getAll should contain entry for current release`() = runTest {
        val notes = repository.getAll().getOrThrow()

        assertNotNull(notes.firstOrNull { it.version == "1.4.0" })
    }
}
