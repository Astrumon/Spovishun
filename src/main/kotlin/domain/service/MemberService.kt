package com.ua.astrumon.domain.service

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberChat
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import com.ua.astrumon.domain.repository.MemberChatRepository
import com.ua.astrumon.domain.repository.MemberRepository
import kotlinx.datetime.Clock

class MemberService(
    private val memberRepository: MemberRepository,
    private val memberChatRepository: MemberChatRepository,
) {

    suspend fun createMember(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        role: MemberRole = MemberRole.MEMBER,
    ): ResultContainer<MemberWithChat> {
        return memberRepository.saveOrUpdate(userId, username, firstName)
            .flatMap { member ->
                memberChatRepository.existsByMemberIdAndChatId(member.id, chatId)
                    .flatMap { exists ->
                        if (exists) {
                            ResultContainer.failure(
                                com.ua.astrumon.common.exception.DuplicateResourceException("Member", username)
                            )
                        } else {
                            memberChatRepository.save(member.id, chatId, role, Clock.System.now())
                                .map { memberChat -> member.toMemberWithChat(memberChat) }
                        }
                    }
            }
    }

    suspend fun getMemberByUsername(username: String): ResultContainer<Member> {
        return memberRepository.findByUsername(username)
            .flatMap { member ->
                if (member != null) ResultContainer.success(member)
                else ResultContainer.failure(ResourceNotFoundException("Member", username))
            }
    }

    suspend fun getMemberWithChatByUsername(chatId: Long, username: String): ResultContainer<MemberWithChat> {
        return memberRepository.findMemberWithChatByChatIdAndUsername(chatId, username)
            .flatMap { memberWithChat ->
                if (memberWithChat != null) ResultContainer.success(memberWithChat)
                else ResultContainer.failure(ResourceNotFoundException("Member", username))
            }
    }

    suspend fun getMemberChatByUserId(chatId: Long, userId: Long): ResultContainer<MemberChat> {
        return memberRepository.findByUserId(userId)
            .flatMap { member ->
                if (member == null) return@flatMap ResultContainer.failure(
                    ResourceNotFoundException("Member", userId.toString())
                )
                memberChatRepository.findByMemberIdAndChatId(member.id, chatId)
                    .flatMap { memberChat ->
                        if (memberChat != null) ResultContainer.success(memberChat)
                        else ResultContainer.failure(ResourceNotFoundException("Member", userId.toString()))
                    }
            }
    }

    suspend fun updateMemberUsername(currentUsername: String, newUsername: String): ResultContainer<Member> {
        return getMemberByUsername(currentUsername)
            .flatMap { member ->
                if (currentUsername == newUsername) {
                    ResultContainer.success(member)
                } else {
                    memberRepository.saveOrUpdate(member.userId, newUsername, member.firstName)
                }
            }
    }

    suspend fun setMemberRole(chatId: Long, userId: Long, role: MemberRole): ResultContainer<MemberChat> {
        return getMemberChatByUserId(chatId, userId)
            .flatMap { memberChat ->
                memberChatRepository.updateRole(memberChat.memberId, chatId, role)
            }
    }

    suspend fun getAllMembersInChat(chatId: Long): ResultContainer<List<MemberWithChat>> {
        return memberRepository.findAllMembersWithChatByChatId(chatId)
    }

    suspend fun hasModeratorAccess(chatId: Long, userId: Long): Boolean {
        return getMemberChatByUserId(chatId, userId)
            .fold(onSuccess = { it.role >= MemberRole.MODERATOR }, onFailure = { false })
    }

    suspend fun hasAdminAccess(chatId: Long, userId: Long): Boolean {
        return getMemberChatByUserId(chatId, userId)
            .fold(onSuccess = { it.role == MemberRole.ADMIN }, onFailure = { false })
    }

    private fun Member.toMemberWithChat(memberChat: MemberChat) = MemberWithChat(
        id = id,
        userId = userId,
        username = username,
        firstName = firstName,
        role = memberChat.role,
        joinedAt = memberChat.joinedAt,
    )
}
