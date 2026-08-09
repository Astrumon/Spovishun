package data.mapper

import com.ua.astrumon.data.bot.mapper.toReleaseNote
import com.ua.astrumon.data.bot.releasenotes.ReleaseNoteDto
import com.ua.astrumon.domain.bot.model.BotLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNoteMapperTest {
    private val ukChanges = listOf("Українська зміна")
    private val enChanges = listOf("English change")

    @Test
    fun `toReleaseNote should pick the changes of the requested language`() {
        val dto = dto(mapOf("uk" to ukChanges, "en" to enChanges))

        assertEquals(ukChanges, dto.toReleaseNote(BotLanguage.UK).changes)
        assertEquals(enChanges, dto.toReleaseNote(BotLanguage.EN).changes)
    }

    @Test
    fun `toReleaseNote should fall back to Ukrainian when the language is missing`() {
        val dto = dto(mapOf("uk" to ukChanges))

        assertEquals(ukChanges, dto.toReleaseNote(BotLanguage.EN).changes)
    }

    @Test
    fun `toReleaseNote should return no changes when the record carries none`() {
        val dto = dto(emptyMap())

        assertTrue(dto.toReleaseNote(BotLanguage.EN).changes.isEmpty())
    }

    @Test
    fun `toReleaseNote should carry version and date through unchanged`() {
        val note = dto(mapOf("uk" to ukChanges)).toReleaseNote(BotLanguage.UK)

        assertEquals("1.2.3", note.version)
        assertEquals("2026-01-01", note.date)
    }

    private fun dto(changes: Map<String, List<String>>) = ReleaseNoteDto(
        version = "1.2.3",
        date = "2026-01-01",
        changes = changes,
    )
}
