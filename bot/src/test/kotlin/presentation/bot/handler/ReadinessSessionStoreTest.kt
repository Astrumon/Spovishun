package presentation.bot.handler

import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionStore
import com.ua.astrumon.presentation.bot.handler.ReadinessVote
import com.ua.astrumon.presentation.bot.handler.SessionKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadinessSessionStoreTest {
    private lateinit var store: ReadinessSessionStore

    private val key = SessionKey(chatId = 1L, messageId = 5L)
    private val alice = Member(1L, 100L, "alice", "Alice")
    private val bob = Member(2L, 200L, "bob", "Bob")
    private val outsiderId = 999L

    @BeforeTest
    fun setup() {
        store = ReadinessSessionStore()
        store.open(key, ReadinessSession(ukMessages, "header", listOf(alice, bob)))
    }

    @Test
    fun `should record a vote for an invited member`() {
        val session = store.vote(key, alice.userId, ReadinessVote.ACCEPTED)

        assertNotNull(session)
        assertEquals(ReadinessVote.ACCEPTED, session.votes[alice.userId])
    }

    @Test
    fun `should overwrite a previous vote instead of counting the member twice`() {
        store.vote(key, alice.userId, ReadinessVote.ACCEPTED)
        val session = store.vote(key, alice.userId, ReadinessVote.DECLINED)

        assertNotNull(session)
        assertEquals(1, session.votes.size)
        assertEquals(ReadinessVote.DECLINED, session.votes[alice.userId])
    }

    @Test
    fun `should reject a vote from someone who was not invited`() {
        val session = store.vote(key, outsiderId, ReadinessVote.ACCEPTED)

        assertNull(session)
        val untouched = assertNotNull(store.get(key))
        assertTrue(untouched.votes.isEmpty())
    }

    @Test
    fun `should reject a vote for an unknown session`() {
        val session = store.vote(SessionKey(1L, 42L), alice.userId, ReadinessVote.ACCEPTED)

        assertNull(session)
    }

    @Test
    fun `should keep every vote when members tap concurrently`() = runTest {
        val voters = (1..VOTER_COUNT).map { Member(it.toLong(), it.toLong(), "u$it", "U$it") }
        val busyKey = SessionKey(2L, 7L)
        store.open(busyKey, ReadinessSession(ukMessages, "header", voters))

        withContext(Dispatchers.Default) {
            voters
                .map { voter -> async { store.vote(busyKey, voter.userId, ReadinessVote.ACCEPTED) } }
                .awaitAll()
        }

        assertEquals(VOTER_COUNT, store.get(busyKey)?.votes?.size)
    }

    @Test
    fun `should return the final state on close and nothing on a second close`() {
        store.vote(key, bob.userId, ReadinessVote.DECLINED)

        val closed = store.close(key)

        assertNotNull(closed)
        assertEquals(ReadinessVote.DECLINED, closed.votes[bob.userId])
        assertNull(store.close(key))
        assertNull(store.get(key))
    }

    private companion object {
        const val VOTER_COUNT = 50
    }
}
