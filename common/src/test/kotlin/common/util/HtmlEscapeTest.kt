package com.ua.astrumon.common.util

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEscapeTest {
    @Test
    fun `escapeHtml should escape ampersand`() {
        assertEquals("a&amp;b", "a&b".escapeHtml())
    }

    @Test
    fun `escapeHtml should escape less-than`() {
        assertEquals("&lt;b&gt;", "<b>".escapeHtml())
    }

    @Test
    fun `escapeHtml should escape greater-than`() {
        assertEquals("&gt;", ">".escapeHtml())
    }

    @Test
    fun `escapeHtml should escape all three special chars in one string`() {
        assertEquals("&lt;script&gt;alert(&amp;)&lt;/script&gt;", "<script>alert(&)</script>".escapeHtml())
    }

    @Test
    fun `escapeHtml should leave safe strings unchanged`() {
        val safe = "hello world 123 @user_name"
        assertEquals(safe, safe.escapeHtml())
    }

    @Test
    fun `escapeHtml should be idempotent on already-escaped input`() {
        val escaped = "&lt;b&gt;"
        assertEquals("&amp;lt;b&amp;gt;", escaped.escapeHtml())
    }

    @Test
    fun `escapeHtml should handle empty string`() {
        assertEquals("", "".escapeHtml())
    }

    @Test
    fun `escapeHtml should escape ampersand before lt and gt to avoid double-escaping`() {
        assertEquals("&amp;&lt;&gt;", "&<>".escapeHtml())
    }
}
