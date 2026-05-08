package domain.model

import com.ua.astrumon.domain.model.BirthDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BirthDateTest {

    @Test
    fun `parse should return BirthDate for valid input`() {
        val result = BirthDate.parse("25.12")
        assertEquals(BirthDate(25, 12), result)
    }

    @Test
    fun `parse should return BirthDate for single-digit day and month`() {
        val result = BirthDate.parse("01.01")
        assertEquals(BirthDate(1, 1), result)
    }

    @Test
    fun `parse should return null for invalid day 99`() {
        assertNull(BirthDate.parse("99.12"))
    }

    @Test
    fun `parse should return null for invalid month 13`() {
        assertNull(BirthDate.parse("01.13"))
    }

    @Test
    fun `parse should return null for wrong format`() {
        assertNull(BirthDate.parse("25-12"))
        assertNull(BirthDate.parse("25"))
        assertNull(BirthDate.parse("abc"))
        assertNull(BirthDate.parse(""))
    }

    @Test
    fun `parse should return null for non-numeric values`() {
        assertNull(BirthDate.parse("xx.yy"))
    }

    @Test
    fun `BirthDate should accept Feb 29 as valid leap day`() {
        val bd = BirthDate(29, 2)
        assertEquals(29, bd.day)
        assertEquals(2, bd.month)
    }

    @Test
    fun `BirthDate should reject Feb 30`() {
        assertFailsWith<IllegalArgumentException> { BirthDate(30, 2) }
    }

    @Test
    fun `BirthDate should reject day 31 for April`() {
        assertFailsWith<IllegalArgumentException> { BirthDate(31, 4) }
    }

    @Test
    fun `BirthDate should reject invalid month`() {
        assertFailsWith<IllegalArgumentException> { BirthDate(1, 13) }
        assertFailsWith<IllegalArgumentException> { BirthDate(1, 0) }
    }

    @Test
    fun `toMmDd and fromMmDd round-trip`() {
        val original = BirthDate(25, 12)
        val encoded = original.toMmDd()
        assertEquals(1225, encoded)
        assertEquals(original, BirthDate.fromMmDd(encoded))
    }

    @Test
    fun `toMmDd and fromMmDd round-trip for Feb 1`() {
        val original = BirthDate(1, 2)
        val encoded = original.toMmDd()
        assertEquals(201, encoded)
        assertEquals(original, BirthDate.fromMmDd(encoded))
    }

    @Test
    fun `format returns DD dot MM string`() {
        assertEquals("25.12", BirthDate(25, 12).format())
        assertEquals("01.01", BirthDate(1, 1).format())
    }
}
