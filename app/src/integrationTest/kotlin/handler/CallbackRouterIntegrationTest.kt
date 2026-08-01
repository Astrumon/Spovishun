package handler

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.bot.handler.AddToGroupCallbackHandler
import com.ua.astrumon.presentation.bot.handler.CallbackRouter
import com.ua.astrumon.presentation.bot.handler.DeleteGroupCallbackHandler
import com.ua.astrumon.presentation.bot.handler.GrantRoleCallbackHandler
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.bot.handler.RandomCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessVote
import com.ua.astrumon.presentation.bot.handler.RemoveFromGroupCallbackHandler
import com.ua.astrumon.presentation.bot.handler.SessionKey
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration coverage for the full callback chain — press → route → handler → reply — over a real
 * PostgreSQL (spovishun-161, audit §8.3).
 *
 * The handlers are unit-tested individually against a mocked `GroupController`, and one delivery
 * path is covered at e2e, but nothing drove a whole press end to end. These cases prioritise the
 * destructive flows, where a mis-routed callback deletes real rows: the assertions are on database
 * state, not on a controller having been called.
 *
 * The router is built from the same handler list `di/PresentationModule.kt` registers, in the same
 * order, since [CallbackRouter] resolves by `startsWith` and takes the first match. Note there are
 * **six** handlers, not the seven the audit's coverage matrix names, and the destructive prefixes
 * are `delgroup:` / `removefrom:` — shorter than the `/delgroup` and `/removefromgroup` commands
 * that open them.
 */
class CallbackRouterIntegrationTest : BaseIntegrationTest() {
    private lateinit var router: CallbackRouter

    @BeforeTest
    fun setUpRouter() {
        router = CallbackRouter(
            listOf(
                PingCallbackHandler(pingController, botAdminUtils, readinessSessionRunner),
                DeleteGroupCallbackHandler(groupController),
                AddToGroupCallbackHandler(groupController),
                RemoveFromGroupCallbackHandler(groupController),
                GrantRoleCallbackHandler(groupController),
                RandomCallbackHandler(randomController, botAdminUtils),
                ReadinessCallbackHandler(readinessSessionRunner),
            ),
        )
    }

    @Test
    fun `a readiness vote routes to the poll and updates the roster`() = runTest {
        val caller = registerMember()
        readinessSessionStore.open(
            SessionKey(testChatId, POLL_MESSAGE_ID),
            ReadinessSession("header", listOf(Member(caller.id, caller.userId, caller.username, caller.firstName))),
        )

        router.route(bot, buildCallbackUpdate("ready:a", messageId = POLL_MESSAGE_ID))

        assertEquals(
            ReadinessVote.ACCEPTED,
            readinessSessionStore.get(SessionKey(testChatId, POLL_MESSAGE_ID))?.votes?.get(caller.userId),
        )
    }

    @Test
    fun `a readiness vote from someone outside the poll is refused with a toast`() = runTest {
        registerMember()
        readinessSessionStore.open(SessionKey(testChatId, POLL_MESSAGE_ID), ReadinessSession("header", emptyList()))

        router.route(bot, buildCallbackUpdate("ready:a", messageId = POLL_MESSAGE_ID))

        assertTrue(
            readinessSessionStore
                .get(SessionKey(testChatId, POLL_MESSAGE_ID))
                ?.votes
                ?.isEmpty() == true,
        )
        verify(exactly = 1) { bot.answerCallbackQuery("cb", text = match { it.contains("не кликали") }) }
    }

    @Test
    fun `delgroup confirm deletes the group`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        val groupId = createGroup("devs")

        router.route(bot, buildCallbackUpdate("delgroup:confirm:$groupId"))

        assertTrue(groupKeys().isEmpty(), "the confirmed delete must remove the group row")
        verify(exactly = 1) {
            bot.sendMessage(ChatId.fromId(testChatId), match { it.contains("devs") }, ParseMode.HTML)
        }
    }

    @Test
    fun `delgroup cancel leaves the group intact`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        createGroup("devs")

        router.route(bot, buildCallbackUpdate("delgroup:cancel"))

        assertEquals(listOf("devs"), groupKeys(), "cancel must not touch the group")
        verify(exactly = 1) { bot.sendMessage(ChatId.fromId(testChatId), any(), ParseMode.HTML) }
    }

    @Test
    fun `delgroup without confirm only offers the confirmation keyboard`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        val groupId = createGroup("devs")

        router.route(bot, buildCallbackUpdate("delgroup:$groupId"))

        assertEquals(listOf("devs"), groupKeys(), "the destructive delete must not run without the confirm step")
        verify(exactly = 1) {
            bot.sendMessage(
                chatId = ChatId.fromId(testChatId),
                text = any(),
                parseMode = ParseMode.HTML,
                replyMarkup = any(),
            )
        }
    }

    @Test
    fun `removefrom removes the member from the group`() = runTest {
        val moderator = registerMember(role = MemberRole.MODERATOR)
        val groupId = createGroup("devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername).getOrThrow()

        // The trailing id is a Telegram user id, not a `members.id`, despite `memberId` naming
        // throughout the callback path: the picker encodes `PickerOption(member.userId, …)` and
        // `GroupController.resolveMemberUsername` matches on `userId`. Passing the row id here
        // resolves nobody and the removal silently no-ops.
        router.route(bot, buildCallbackUpdate("removefrom:$groupId:${moderator.userId}"))

        assertTrue(groupMembers("devs").isEmpty(), "the membership row must be gone")
        assertEquals(
            moderator.id,
            memberService.getMemberByUsername(testUsername).getOrThrow().id,
            "removing a membership must not delete the member",
        )
    }

    @Test
    fun `removefrom with only a group id opens the picker and removes nobody`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        val groupId = createGroup("devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername).getOrThrow()

        router.route(bot, buildCallbackUpdate("removefrom:$groupId"))

        assertEquals(listOf(testUsername), groupMembers("devs"), "step one must only list, never remove")
    }

    @Test
    fun `an unknown callback prefix is acknowledged and routed nowhere`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        createGroup("devs")

        router.route(bot, buildCallbackUpdate("zzz:$UNKNOWN_PAYLOAD"))

        assertEquals(listOf("devs"), groupKeys(), "an unroutable callback must not reach a handler")
        verify(exactly = 1) { bot.answerCallbackQuery("cb") }
        verify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `a non-moderator cannot delete a group through the confirm callback`() = runTest {
        registerMember(role = MemberRole.MEMBER)
        val groupId = createGroup("devs")

        router.route(bot, buildCallbackUpdate("delgroup:confirm:$groupId"))

        assertEquals(listOf("devs"), groupKeys(), "the role gate must hold through the whole routing chain")
        verify(exactly = 1) {
            bot.sendMessage(ChatId.fromId(testChatId), match { it.contains("адмін") }, ParseMode.HTML)
        }
    }

    private suspend fun createGroup(name: String): Long = groupService.createGroup(testChatId, name).getOrThrow().id

    private suspend fun groupKeys(): List<String> = groupService.getAllGroupsWithMembers(testChatId).getOrThrow().map { it.key }

    private suspend fun groupMembers(key: String): List<String> = groupService.getGroupByKey(testChatId, key).getOrThrow().members

    private companion object {
        const val UNKNOWN_PAYLOAD = "1"

        /** Message the readiness poll is keyed on — any id works, it just has to match the callback. */
        const val POLL_MESSAGE_ID = 77L
    }
}
