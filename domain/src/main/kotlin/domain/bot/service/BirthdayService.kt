package com.ua.astrumon.domain.bot.service

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.repository.BirthdayGreetingRepository
import com.ua.astrumon.domain.bot.repository.MemberChatRepository
import com.ua.astrumon.domain.bot.repository.MemberRepository
import kotlinx.datetime.Instant

class BirthdayService(
    private val memberRepository: MemberRepository,
    private val memberChatRepository: MemberChatRepository,
    private val birthdayGreetingRepository: BirthdayGreetingRepository,
) {
    suspend fun setBirthday(
        userId: Long,
        birthday: BirthDate,
    ): ResultContainer<Member> = memberRepository.findByUserId(userId).flatMap { member ->
        if (member == null) {
            ResultContainer.failure(ResourceNotFoundException("Member", userId.toString()))
        } else {
            memberRepository.updateBirthday(userId, birthday)
        }
    }

    suspend fun setBirthdayForUsername(
        username: String,
        birthday: BirthDate,
    ): ResultContainer<Member> = memberRepository.findByUsername(username).flatMap { member ->
        if (member == null) {
            ResultContainer.failure(ResourceNotFoundException("Member", username))
        } else {
            memberRepository.updateBirthday(member.userId, birthday)
        }
    }

    suspend fun clearBirthday(userId: Long): ResultContainer<Member> = memberRepository.updateBirthday(userId, null)

    suspend fun getMembersWithBirthday(birthday: BirthDate): ResultContainer<List<Member>> =
        memberRepository.findAllByBirthMd(birthday.toMmDd())

    suspend fun wasGreetedThisYear(
        memberId: Long,
        year: Int,
    ): ResultContainer<Boolean> = birthdayGreetingRepository.wasSent(memberId, year)

    suspend fun recordGreetingSent(
        memberId: Long,
        year: Int,
        sentAt: Instant,
    ): ResultContainer<Unit> = birthdayGreetingRepository.markSent(memberId, year, sentAt)

    suspend fun findChatIdsForMember(memberId: Long): ResultContainer<List<Long>> = memberChatRepository.findChatIdsByMemberId(memberId)
}
