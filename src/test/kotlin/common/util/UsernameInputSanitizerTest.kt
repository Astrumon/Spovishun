package com.ua.astrumon.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsernameInputSanitizerTest {
    // --- normalizeUsername ---

    @Test
    fun `should_returnLowercaseUsername_when_validLowercaseInput`() {
        assertEquals("alice", UsernameInputSanitizer.normalizeUsername("alice"))
    }

    @Test
    fun `should_stripAtAndLowercase_when_atPrefixPresent`() {
        assertEquals("alice", UsernameInputSanitizer.normalizeUsername("@alice"))
    }

    @Test
    fun `should_trimAndNormalize_when_surroundedBySpaces`() {
        assertEquals("alice", UsernameInputSanitizer.normalizeUsername("  @alice  "))
    }

    @Test
    fun `should_lowercase_when_mixedCaseInput`() {
        assertEquals("alice123", UsernameInputSanitizer.normalizeUsername("Alice123"))
    }

    @Test
    fun `should_returnNull_when_emptyString`() {
        assertNull(UsernameInputSanitizer.normalizeUsername(""))
    }

    @Test
    fun `should_returnNull_when_blankString`() {
        assertNull(UsernameInputSanitizer.normalizeUsername("   "))
    }

    @Test
    fun `should_returnNull_when_onlyAtSign`() {
        assertNull(UsernameInputSanitizer.normalizeUsername("@"))
    }

    @Test
    fun `should_returnNull_when_containsExclamationMark`() {
        assertNull(UsernameInputSanitizer.normalizeUsername("user!name"))
    }

    @Test
    fun `should_returnNull_when_containsDash`() {
        assertNull(UsernameInputSanitizer.normalizeUsername("user-name"))
    }

    @Test
    fun `should_returnNull_when_containsCyrillic`() {
        assertNull(UsernameInputSanitizer.normalizeUsername("іванko"))
    }

    @Test
    fun `should_returnNull_when_tooLong`() {
        assertNull(UsernameInputSanitizer.normalizeUsername("a".repeat(33)))
    }

    @Test
    fun `should_returnUsername_when_exactly32CharsLong`() {
        val username = "a".repeat(32)
        assertEquals(username, UsernameInputSanitizer.normalizeUsername(username))
    }

    @Test
    fun `should_returnUsername_when_singleChar`() {
        assertEquals("a", UsernameInputSanitizer.normalizeUsername("a"))
    }

    @Test
    fun `should_allowUnderscore_when_underscoreInUsername`() {
        assertEquals("user_name", UsernameInputSanitizer.normalizeUsername("user_name"))
    }

    // --- parseUsernames ---

    @Test
    fun `should_parseAllValid_when_commaSeparatedWithAt`() {
        val result = UsernameInputSanitizer.parseUsernames("@user1,@user2,@user3")
        assertEquals(listOf("user1", "user2", "user3"), result.valid)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_parseAllValid_when_spaceSeparatedWithAt`() {
        val result = UsernameInputSanitizer.parseUsernames("@user1 @user2 @user3")
        assertEquals(listOf("user1", "user2", "user3"), result.valid)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_parseAllValid_when_mixedCommaAndSpace`() {
        val result = UsernameInputSanitizer.parseUsernames("@user1, @user2 @user3")
        assertEquals(listOf("user1", "user2", "user3"), result.valid)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_acceptWithoutAt_when_noAtPrefix`() {
        val result = UsernameInputSanitizer.parseUsernames("user1 user2")
        assertEquals(listOf("user1", "user2"), result.valid)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_separateInvalid_when_mixedValidAndInvalidTokens`() {
        val result = UsernameInputSanitizer.parseUsernames("@user1 user!bad")
        assertEquals(listOf("user1"), result.valid)
        assertEquals(listOf("user!bad"), result.invalid)
    }

    @Test
    fun `should_deduplicateByLowercase_when_duplicatesWithVariousFormats`() {
        val result = UsernameInputSanitizer.parseUsernames("@user1 user1 USER1")
        assertEquals(listOf("user1"), result.valid)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_deduplicateAcrossAtAndNoAt_when_sameUsernameWithAndWithoutAt`() {
        val result = UsernameInputSanitizer.parseUsernames("@alice alice @ALICE")
        assertEquals(listOf("alice"), result.valid)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_returnEmpty_when_emptyInput`() {
        val result = UsernameInputSanitizer.parseUsernames("")
        assertTrue(result.valid.isEmpty())
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_returnEmpty_when_onlySpaces`() {
        val result = UsernameInputSanitizer.parseUsernames("   ")
        assertTrue(result.valid.isEmpty())
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_parseTabAndNewlineSeparated_when_whitespaceDelimiters`() {
        val result = UsernameInputSanitizer.parseUsernames("@user1\t@user2\n@user3")
        assertEquals(listOf("user1", "user2", "user3"), result.valid)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `should_preserveInsertionOrder_when_validUsernames`() {
        val result = UsernameInputSanitizer.parseUsernames("@charlie @alice @bob")
        assertEquals(listOf("charlie", "alice", "bob"), result.valid)
    }

    @Test
    fun `should_collectAllInvalid_when_multipleInvalidTokens`() {
        val result = UsernameInputSanitizer.parseUsernames("user!bad @valid user-bad")
        assertEquals(listOf("valid"), result.valid)
        assertEquals(listOf("user!bad", "user-bad"), result.invalid)
    }
}
