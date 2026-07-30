package commands

import com.ua.astrumon.presentation.bot.BotMessages
import infrastructure.BaseE2ETest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Registration outcomes are covered in `RegisterCommandIntegrationTest`; this checks that the reply
 * a user would actually see arrives in the chat unchanged.
 */
class RegisterCommandE2ETest : BaseE2ETest() {
    /**
     * The second `/register` is the deterministic one: whatever role the live chat reports for the
     * helper bot on the first call, the follow-up must render the already-registered copy.
     */
    @Test
    fun `second register delivers the already-registered reply to the chat`() {
        dispatch("/register")

        val sent = dispatchExpectingReply("/register")

        assertEquals(
            BotMessages.Success.prefix + BotMessages.Registration.alreadyRegistered("HelperBot"),
            sent.text,
        )
        assertEquals(1, allMembers().count { it.userId == helperBotId }, "The repeat must not duplicate the member")
    }
}
