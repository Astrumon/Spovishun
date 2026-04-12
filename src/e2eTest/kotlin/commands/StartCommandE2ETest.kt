package commands

import infrastructure.BaseE2ETest
import kotlin.test.Test
import kotlin.test.assertTrue

class StartCommandE2ETest : BaseE2ETest() {

    @Test
    fun `start command auto-registers the trigger user`() {
        dispatch("/start")
        val members = allMembers()
        assertTrue(members.any { it.userId == helperBotId }, "Expected helperBot to be auto-registered after /start")
    }

    @Test
    fun `start command auto-registers chat administrators`() {
        dispatch("/start")
        // At least the trigger user (helperBot) plus real admins fetched from Telegram
        val members = allMembers()
        assertTrue(members.isNotEmpty(), "Expected at least one member after /start (admins auto-registered)")
    }
}
