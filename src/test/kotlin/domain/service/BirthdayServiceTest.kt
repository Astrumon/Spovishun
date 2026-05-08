package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.BirthDate
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.repository.BirthdayGreetingRepository
import com.ua.astrumon.domain.repository.MemberChatRepository
import com.ua.astrumon.domain.repository.MemberRepository
import com.ua.astrumon.domain.service.BirthdayService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BirthdayServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val memberChatRepository: MemberChatRepository = mockk()
    private val birthdayGreetingRepository: BirthdayGreetingRepository = mockk()
    private lateinit var service: BirthdayService

    private val member = Member(1L, 100L, "alice", "Alice", BirthDate(25, 12))

    @BeforeTest
    fun setup() {
        clearAllMocks()
        service = BirthdayService(memberRepository, memberChatRepository, birthdayGreetingRepository)
    }

    // --- setBirthday ---

    @Test
    fun `setBirthday should update and return member when found`() = runTest {
        val birthday = BirthDate(25, 12)
        coEvery { memberRepository.findByUserId(100L) } returns ResultContainer.success(member)
        coEvery { memberRepository.updateBirthday(100L, birthday) } returns ResultContainer.success(member)

        val result = service.setBirthday(100L, birthday)

        assertTrue(result.isSuccess)
        assertEquals(member, result.getOrThrow())
        coVerify { memberRepository.updateBirthday(100L, birthday) }
    }

    @Test
    fun `setBirthday should return failure when member not found`() = runTest {
        val birthday = BirthDate(1, 1)
        coEvery { memberRepository.findByUserId(999L) } returns ResultContainer.success(null)

        val result = service.setBirthday(999L, birthday)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    // --- setBirthdayForUsername ---

    @Test
    fun `setBirthdayForUsername should update birthday when username exists`() = runTest {
        val birthday = BirthDate(1, 1)
        coEvery { memberRepository.findByUsername("alice") } returns ResultContainer.success(member)
        coEvery { memberRepository.updateBirthday(100L, birthday) } returns ResultContainer.success(member)

        val result = service.setBirthdayForUsername("alice", birthday)

        assertTrue(result.isSuccess)
        coVerify { memberRepository.updateBirthday(100L, birthday) }
    }

    @Test
    fun `setBirthdayForUsername should return failure when username not found`() = runTest {
        val birthday = BirthDate(5, 5)
        coEvery { memberRepository.findByUsername("unknown") } returns ResultContainer.success(null)

        val result = service.setBirthdayForUsername("unknown", birthday)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    // --- clearBirthday ---

    @Test
    fun `clearBirthday should call updateBirthday with null`() = runTest {
        coEvery { memberRepository.updateBirthday(100L, null) } returns ResultContainer.success(member.copy(birthday = null))

        val result = service.clearBirthday(100L)

        assertTrue(result.isSuccess)
        coVerify { memberRepository.updateBirthday(100L, null) }
    }

    // --- getMembersWithBirthday ---

    @Test
    fun `getMembersWithBirthday should return members on given date`() = runTest {
        val birthday = BirthDate(25, 12)
        coEvery { memberRepository.findAllByBirthMd(1225) } returns ResultContainer.success(listOf(member))

        val result = service.getMembersWithBirthday(birthday)

        assertTrue(result.isSuccess)
        assertEquals(listOf(member), result.getOrThrow())
    }

    // --- wasGreetedThisYear ---

    @Test
    fun `wasGreetedThisYear delegates to repository`() = runTest {
        coEvery { birthdayGreetingRepository.wasSent(1L, 2025) } returns ResultContainer.success(true)

        val result = service.wasGreetedThisYear(1L, 2025)

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow())
    }

    // --- recordGreetingSent ---

    @Test
    fun `recordGreetingSent delegates to repository`() = runTest {
        val instant = Instant.fromEpochMilliseconds(0)
        coEvery { birthdayGreetingRepository.markSent(1L, 2025, instant) } returns ResultContainer.success(Unit)

        val result = service.recordGreetingSent(1L, 2025, instant)

        assertTrue(result.isSuccess)
        coVerify { birthdayGreetingRepository.markSent(1L, 2025, instant) }
    }

    // --- findChatIdsForMember ---

    @Test
    fun `findChatIdsForMember should return chat ids from repository`() = runTest {
        coEvery { memberChatRepository.findChatIdsByMemberId(1L) } returns ResultContainer.success(listOf(-100L, -200L))

        val result = service.findChatIdsForMember(1L)

        assertTrue(result.isSuccess)
        assertEquals(listOf(-100L, -200L), result.getOrThrow())
    }

    @Test
    fun `findChatIdsForMember should propagate repository failure`() = runTest {
        coEvery { memberChatRepository.findChatIdsByMemberId(1L) } returns
            ResultContainer.failure(DatabaseException("DB error"))

        val result = service.findChatIdsForMember(1L)

        assertTrue(result.isFailure)
    }
}
