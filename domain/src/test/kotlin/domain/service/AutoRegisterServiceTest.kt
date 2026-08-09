package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.cache.ChatCache
import com.ua.astrumon.domain.bot.cache.UserCache
import com.ua.astrumon.domain.bot.model.Chat
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.MemberService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoRegisterServiceTest {
    private val memberService: MemberService = mockk()
    private val chatService: ChatService = mockk()
    private lateinit var userCache: UserCache
    private lateinit var chatCache: ChatCache
    private lateinit var autoRegisterService: AutoRegisterService

    private val chatId = 123L
    private val userId = 456L
    private val username = "alice"
    private val firstName = "Alice"
    private val userRole = MemberRole.MEMBER

    private val memberWithChat = MemberWithChat(
        id = 1L,
        userId = userId,
        username = username,
        firstName = firstName,
        role = userRole,
        joinedAt = null,
    )

    @BeforeTest
    fun setup() {
        clearAllMocks()
        userCache = UserCache()
        chatCache = ChatCache()
        autoRegisterService = AutoRegisterService(memberService, chatService, userCache, chatCache)
        coEvery { chatService.ensureChat(any(), any(), any()) } returns ResultContainer.success(
            Chat(chatId, null, null, Clock.System.now()),
        )
    }

    /**
     * The supplier exists so an already-known member never pays for the caller's role lookup, which
     * on the bot side is a blocking Telegram round trip (spovishun-172).
     */
    @Test
    fun `ensureUserRegistered should not ask for the role when the member already exists`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.success(memberWithChat)
        var roleAsked = false

        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, {
            roleAsked = true
            userRole
        })

        assertFalse(roleAsked)
    }

    @Test
    fun `ensureUserRegistered should ask for the role when it has to create the member`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns
            ResultContainer.failure(ResourceNotFoundException("Member", username))
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = userRole) } returns
            ResultContainer.success(memberWithChat)
        var roleAsked = false

        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, {
            roleAsked = true
            userRole
        })

        assertTrue(roleAsked)
    }

    @Test
    fun `ensureUserRegistered should return existing member when already registered in chat`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.success(memberWithChat)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })

        assertTrue(result.isSuccess)
        assertEquals(memberWithChat, result.getOrThrow())
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should create new member when not in this chat`() = runTest {
        val notFoundError = ResourceNotFoundException("Member", username)
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = userRole) } returns
            ResultContainer.success(memberWithChat)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })

        assertTrue(result.isSuccess)
        assertEquals(memberWithChat, result.getOrThrow())
        coVerify { memberService.createMember(chatId, userId, username, firstName, role = userRole) }
    }

    @Test
    fun `ensureUserRegistered should return validation error when userId is invalid`() = runTest {
        val result = autoRegisterService.ensureUserRegistered(chatId, -1L, username, firstName, { userRole })

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        coVerify(exactly = 0) { memberService.getMemberWithChatByUsername(any(), any()) }
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should return failure when member creation fails with non-duplicate error`() = runTest {
        val notFoundError = ResourceNotFoundException("Member", username)
        val dbError = DatabaseException("DB down")
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = userRole) } returns
            ResultContainer.failure(dbError)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }

    @Test
    fun `ensureUserRegistered should return failure when member lookup fails with non-notfound error`() = runTest {
        val error = DatabaseException("Database error")
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(error)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should register admin with ADMIN role`() = runTest {
        val adminRole = MemberRole.ADMIN
        val adminMemberWithChat = memberWithChat.copy(role = adminRole)
        val notFoundError = ResourceNotFoundException("Member", username)
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = adminRole) } returns
            ResultContainer.success(adminMemberWithChat)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { adminRole })

        assertTrue(result.isSuccess)
        assertEquals(MemberRole.ADMIN, result.getOrThrow().role)
        coVerify { memberService.createMember(chatId, userId, username, firstName, role = adminRole) }
    }

    @Test
    fun `isUserRegistered should return true when member exists in chat`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.success(memberWithChat)

        assertTrue(autoRegisterService.isUserRegistered(chatId, username).getOrThrow())
    }

    @Test
    fun `isUserRegistered should return false when member does not exist`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(
            ResourceNotFoundException("Member", username),
        )

        assertFalse(autoRegisterService.isUserRegistered(chatId, username).getOrThrow())
    }

    @Test
    fun `isUserRegistered should return failure when lookup fails`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(
            DatabaseException("DB error"),
        )

        val result = autoRegisterService.isUserRegistered(chatId, username)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }

    // --- cache ---

    @Test
    fun `ensureUserRegistered should return cached member without DB call on second invocation`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.success(memberWithChat)

        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })

        // DB should be queried only once; second call is a cache hit
        coVerify(exactly = 1) { memberService.getMemberWithChatByUsername(chatId, username) }
    }

    @Test
    fun `ensureUserRegistered should populate cache after creating a new member`() = runTest {
        val notFoundError = ResourceNotFoundException("Member", username)
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = userRole) } returns
            ResultContainer.success(memberWithChat)

        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })

        // getMemberWithChatByUsername called once; second call served from cache
        coVerify(exactly = 1) { memberService.getMemberWithChatByUsername(chatId, username) }
        coVerify(exactly = 1) { memberService.createMember(chatId, userId, username, firstName, role = userRole) }
    }

    @Test
    fun `ensureUserRegistered should skip ensureChat when chat is already cached`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.success(memberWithChat)

        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, { userRole })

        // ensureChat called only on first invocation
        coVerify(exactly = 1) { chatService.ensureChat(chatId, any(), any()) }
    }

    @Test
    fun `isUserRegistered should return true from cache without DB call`() = runTest {
        userCache.put(chatId, username, memberWithChat)

        assertTrue(autoRegisterService.isUserRegistered(chatId, username).getOrThrow())
        coVerify(exactly = 0) { memberService.getMemberWithChatByUsername(any(), any()) }
    }
}
