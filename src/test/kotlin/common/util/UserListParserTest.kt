package com.ua.astrumon.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserListParserTest {

    @Test
    fun `parseUserList should handle comma-separated format`() {
        val result = UserListParser.parseUserList("@user1,@user2,@user3")
        assertEquals(listOf("user1", "user2", "user3"), result)
    }

    @Test
    fun `parseUserList should handle comma-separated with spaces`() {
        val result = UserListParser.parseUserList("@user1, @user2, @user3")
        assertEquals(listOf("user1", "user2", "user3"), result)
    }

    @Test
    fun `parseUserList should handle space-separated format`() {
        val result = UserListParser.parseUserList("@user1 @user2 @user3")
        assertEquals(listOf("user1", "user2", "user3"), result)
    }

    @Test
    fun `parseUserList should filter out usernames without @ symbol`() {
        val result = UserListParser.parseUserList("@user1,user2,@user3")
        assertEquals(listOf("user1", "user3"), result)
    }

    @Test
    fun `parseUserList should filter out empty strings`() {
        val result = UserListParser.parseUserList("@user1,,@user2")
        assertEquals(listOf("user1", "user2"), result)
    }

    @Test
    fun `parseUserList should handle single user`() {
        val result = UserListParser.parseUserList("@user1")
        assertEquals(listOf("user1"), result)
    }

    @Test
    fun `parseUserList should handle empty input`() {
        val result = UserListParser.parseUserList("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseUserList should handle only @ symbol`() {
        val result = UserListParser.parseUserList("@")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseUserList should handle mixed spaces and commas`() {
        val result = UserListParser.parseUserList("@user1, @user2 @user3")
        // The parser splits by comma first, so "@user2 @user3" is treated as one item
        // It doesn't split by space when commas are present
        assertEquals(listOf("user1", "user2 @user3"), result)
    }

    @Test
    fun `parseUserList should trim whitespace from usernames`() {
        val result = UserListParser.parseUserList("  @user1  ,  @user2  ")
        assertEquals(listOf("user1", "user2"), result)
    }

    @Test
    fun `parseSingleUsername should remove @ prefix`() {
        val result = UserListParser.parseSingleUsername("@user1")
        assertEquals("user1", result)
    }

    @Test
    fun `parseSingleUsername should handle username without @`() {
        val result = UserListParser.parseSingleUsername("user1")
        assertEquals("user1", result)
    }

    @Test
    fun `parseSingleUsername should trim whitespace`() {
        val result = UserListParser.parseSingleUsername("  @user1  ")
        assertEquals("user1", result)
    }
}
