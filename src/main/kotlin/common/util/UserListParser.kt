package com.ua.astrumon.common.util

/**
 * Utility for parsing user lists from command arguments.
 * Supports flexible input formats for specifying multiple users.
 */
object UserListParser {
    /**
     * Parses a string containing multiple usernames with mandatory @ symbol.
     * Supported formats:
     * - @user1,@user2,@user3 (comma-separated, no spaces)
     * - @user1, @user2, @user3 (comma-separated with spaces)
     * - @user1 @user2 @user3 (space-separated)
     *
     * @param userList The string containing the user list
     * @return List of usernames without @ prefix
     */
    fun parseUserList(userList: String): List<String> =
        userList
            // Handle comma-separated format
            .split(",")
            .map { it.trim() }
            // If no commas found, try space-separated format
            .let { items ->
                if (items.size == 1 && items[0].contains(" ")) {
                    items[0].split("\\s+".toRegex())
                } else {
                    items
                }
            }.filter { it.startsWith("@") }
            .map { it.trim().removePrefix("@") }
            .filter { it.isNotEmpty() }

    /**
     * Parses a single username from a string, removing the @ prefix if present.
     * This is a simplified version for commands that accept only a single user.
     *
     * @param username The username string, optionally with @ prefix
     * @return Username without @ prefix
     */
    fun parseSingleUsername(username: String): String = username.trim().removePrefix("@")
}
