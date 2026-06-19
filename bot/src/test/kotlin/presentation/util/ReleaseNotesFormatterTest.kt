package presentation.util

import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.model.ReleaseNote
import com.ua.astrumon.presentation.util.ReleaseNotesFormatter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseNotesFormatterTest {
    private val singleNote = ReleaseNote("2.0.0", "2026-01-01", listOf("Feature A", "Feature B"))
    private val oldNote = ReleaseNote("1.0.0", "2025-01-01", listOf("Initial release"))

    @Test
    fun `formatLatest should include version and date`() {
        val result = requireNotNull(ReleaseNotesFormatter.formatLatest(listOf(singleNote)))

        assertContains(result, "2.0.0")
        assertContains(result, "2026-01-01")
    }

    @Test
    fun `formatLatest should include all changes as bullet points`() {
        val result = requireNotNull(ReleaseNotesFormatter.formatLatest(listOf(singleNote)))

        assertContains(result, "Feature A")
        assertContains(result, "Feature B")
        assertTrue(result.contains("•"))
    }

    @Test
    fun `formatLatest should include bot name`() {
        val result = requireNotNull(ReleaseNotesFormatter.formatLatest(listOf(singleNote)))

        assertContains(result, VersionInfo.BOT_NAME)
    }

    @Test
    fun `formatLatest should return null for empty list`() {
        val result = ReleaseNotesFormatter.formatLatest(emptyList())

        assertNull(result)
    }

    @Test
    fun `formatLatest should use first note when multiple provided`() {
        val result = requireNotNull(ReleaseNotesFormatter.formatLatest(listOf(singleNote, oldNote)))

        assertContains(result, "2.0.0")
        assertTrue(!result.contains("1.0.0"))
    }

    @Test
    fun `formatHistory should include all versions`() {
        val result = ReleaseNotesFormatter.formatHistory(listOf(singleNote, oldNote))

        assertContains(result, "2.0.0")
        assertContains(result, "1.0.0")
    }

    @Test
    fun `formatHistory should include history title`() {
        val result = ReleaseNotesFormatter.formatHistory(listOf(singleNote))

        assertTrue(result.isNotBlank())
        assertTrue(result.indexOf("2.0.0") > 0)
    }

    @Test
    fun `formatHistory should include changes for all versions`() {
        val result = ReleaseNotesFormatter.formatHistory(listOf(singleNote, oldNote))

        assertContains(result, "Feature A")
        assertContains(result, "Initial release")
    }

    @Test
    fun `formatLatest should wrap version in bold HTML tag`() {
        val result = requireNotNull(ReleaseNotesFormatter.formatLatest(listOf(singleNote)))

        assertTrue(result.contains("<b>") && result.contains("</b>"))
    }
}
