package presentation.util

import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.util.toHtmlMention
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberMentionTest {
    @Test
    fun `should render at-username mention when username is present`() {
        val member = Member(id = 1L, userId = 100L, username = "alice", firstName = "Alice")

        assertEquals("@alice", member.toHtmlMention())
    }

    @Test
    fun `should render tg user link fallback when username is synthetic user_id`() {
        val member = Member(id = 1L, userId = 100L, username = "user_100", firstName = "Alice")

        assertEquals("<a href=\"tg://user?id=100\">Alice</a>", member.toHtmlMention())
    }

    @Test
    fun `should render tg user link fallback when username is blank`() {
        val member = Member(id = 1L, userId = 100L, username = "", firstName = "Alice")

        assertEquals("<a href=\"tg://user?id=100\">Alice</a>", member.toHtmlMention())
    }

    @Test
    fun `should escape html special chars in first name inside fallback anchor`() {
        val member = Member(id = 1L, userId = 100L, username = "user_100", firstName = "Богдан 🧟‍♂️ <b>&Co")

        assertEquals("<a href=\"tg://user?id=100\">Богдан 🧟‍♂️ &lt;b&gt;&amp;Co</a>", member.toHtmlMention())
    }

    @Test
    fun `should keep at-username when synthetic pattern does not match own userId`() {
        val member = Member(id = 1L, userId = 100L, username = "user_999", firstName = "Alice")

        assertEquals("@user_999", member.toHtmlMention())
    }
}
