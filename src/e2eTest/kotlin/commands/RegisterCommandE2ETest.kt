package commands

import infrastructure.BaseE2ETest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisterCommandE2ETest : BaseE2ETest() {

    @Test
    fun `register command creates the sender in the repository`() {
        dispatch("/register")
        val members = allMembers()
        assertTrue(members.any { it.userId == helperBotId }, "Expected helperBot to be registered after /register")
    }

    @Test
    fun `register command is idempotent — duplicate dispatch keeps one member`() {
        dispatch("/register")
        dispatch("/register")
        val members = allMembers()
        assertEquals(1, members.count { it.userId == helperBotId }, "Expected exactly one entry for helperBot after duplicate /register")
    }
}
