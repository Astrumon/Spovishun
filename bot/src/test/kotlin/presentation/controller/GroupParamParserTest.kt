package presentation.controller

import com.ua.astrumon.presentation.controller.GroupParam
import com.ua.astrumon.presentation.controller.GroupParamParseResult
import com.ua.astrumon.presentation.controller.GroupParamParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupParamParserTest {
    @Test
    fun `parse should report Show when there are no tokens`() {
        assertIs<GroupParamParseResult.Show>(GroupParamParser.parse(emptyList()))
    }

    @Test
    fun `parse should read a single parameter`() {
        val result = GroupParamParser.parse(listOf("\$icon=🔥"))

        assertIs<GroupParamParseResult.Edit>(result)
        assertEquals(mapOf(GroupParam.ICON to "🔥"), result.values)
    }

    @Test
    fun `parse should read every parameter in one call`() {
        val result = GroupParamParser.parse(listOf("\$icon=🔥", "\$mark=🦀", "\$name=devops"))

        assertIs<GroupParamParseResult.Edit>(result)
        assertEquals(
            mapOf(GroupParam.ICON to "🔥", GroupParam.MARK to "🦀", GroupParam.NAME to "devops"),
            result.values,
        )
    }

    @Test
    fun `parse should accept a flag in any case`() {
        val result = GroupParamParser.parse(listOf("\$ICON=🔥"))

        assertIs<GroupParamParseResult.Edit>(result)
        assertEquals(mapOf(GroupParam.ICON to "🔥"), result.values)
    }

    /** Only the first `=` separates; a second one belongs to the value and fails validation later. */
    @Test
    fun `parse should split on the first separator only`() {
        val result = GroupParamParser.parse(listOf("\$name=a=b"))

        assertIs<GroupParamParseResult.Edit>(result)
        assertEquals(mapOf(GroupParam.NAME to "a=b"), result.values)
    }

    @Test
    fun `parse should reject a known parameter written without a separator`() {
        val result = GroupParamParser.parse(listOf("\$icon", "🔥"))

        assertIs<GroupParamParseResult.Failure.MissingSeparator>(result)
        assertEquals(GroupParam.ICON, result.param)
    }

    @Test
    fun `parse should reject an unknown parameter`() {
        val result = GroupParamParser.parse(listOf("\$nope=1"))

        assertIs<GroupParamParseResult.Failure.UnknownParameter>(result)
        assertEquals("\$nope", result.token)
    }

    @Test
    fun `parse should reject an unknown flag written without a separator`() {
        val result = GroupParamParser.parse(listOf("\$nope"))

        assertIs<GroupParamParseResult.Failure.UnknownParameter>(result)
        assertEquals("\$nope", result.token)
    }

    @Test
    fun `parse should reject a token that is not a parameter`() {
        val result = GroupParamParser.parse(listOf("abracadabra"))

        assertIs<GroupParamParseResult.Failure.NotAParameter>(result)
        assertEquals("abracadabra", result.token)
    }

    /** Trailing junk after valid parameters is a typo, never something to silently drop. */
    @Test
    fun `parse should reject junk that follows valid parameters`() {
        val result = GroupParamParser.parse(listOf("\$icon=🔥", "junk"))

        assertIs<GroupParamParseResult.Failure.NotAParameter>(result)
        assertEquals("junk", result.token)
    }

    @Test
    fun `parse should reject a duplicated parameter`() {
        val result = GroupParamParser.parse(listOf("\$mark=🔥", "\$mark=🦀"))

        assertIs<GroupParamParseResult.Failure.DuplicateParameter>(result)
        assertEquals(GroupParam.MARK, result.param)
    }

    @Test
    fun `parse should reject an empty value`() {
        val result = GroupParamParser.parse(listOf("\$mark="))

        assertIs<GroupParamParseResult.Failure.EmptyValue>(result)
        assertEquals(GroupParam.MARK, result.param)
    }

    /** Spaces around `=` make three tokens, none of which is a pair — the first one fails. */
    @Test
    fun `parse should reject spaces around the separator`() {
        val result = GroupParamParser.parse(listOf("\$mark", "=", "🦀"))

        assertIs<GroupParamParseResult.Failure.MissingSeparator>(result)
        assertEquals(GroupParam.MARK, result.param)
    }

    @Test
    fun `supported should name every parameter`() {
        val supported = GroupParam.supported()

        GroupParam.entries.forEach { assertEquals(true, supported.contains(it.flag), "${it.flag} is missing") }
    }
}
