package com.ua.astrumon.domain.service

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.repository.MemberRepository
import kotlinx.datetime.Clock

class MemberService(
    private val memberRepository: MemberRepository
) {

    suspend fun createMember(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        role: MemberRole = MemberRole.MEMBER
    ): ResultContainer<Member> {
        return checkUsernameExists(username)
            .flatMap {
                memberRepository.save(
                    chatId = chatId,
                    userId = userId,
                    username = username,
                    firstName = firstName,
                    role = role,
                    joinedAt = Clock.System.now()
                )
            }
    }

    suspend fun getMemberByUsername(username: String): ResultContainer<Member> {
        return memberRepository.findByUsername(username)
            .flatMap { member ->
                if (member != null) {
                    ResultContainer.success(member)
                } else {
                    ResultContainer.failure(ResourceNotFoundException("Member", username))
                }
            }
    }

    suspend fun updateMemberUsername(currentUsername: String, newUsername: String): ResultContainer<Member> {
        return getMemberByUsername(currentUsername)
            .flatMap { currentMember ->
                if (currentUsername == newUsername) {
                    ResultContainer.success(currentMember)
                } else {
                    checkUsernameExists(newUsername)
                        .flatMap {
                            memberRepository.save(
                                chatId = currentMember.chatId,
                                userId = currentMember.userId,
                                username = newUsername,
                                firstName = currentMember.firstName,
                                joinedAt = currentMember.joinedAt
                            )
                        }
                }
            }
    }

    suspend fun getMemberByChatAndUserId(chatId: Long, userId: Long): ResultContainer<Member> {
        return memberRepository.findByChatIdAndUserId(chatId, userId)
            .flatMap { member ->
                if (member != null) {
                    ResultContainer.success(member)
                } else {
                    ResultContainer.failure(ResourceNotFoundException("Member", userId.toString()))
                }
            }
    }

    suspend fun setMemberRole(chatId: Long, userId: Long, role: MemberRole): ResultContainer<Member> {
        return getMemberByChatAndUserId(chatId, userId)
            .flatMap { memberRepository.updateRole(chatId, userId, role) }
    }

    suspend fun getAllMembers(): ResultContainer<List<Member>> {
        return memberRepository.findAll()
    }

    suspend fun hasModeratorAccess(chatId: Long, userId: Long): Boolean {
        return getMemberByChatAndUserId(chatId, userId)
            .fold(onSuccess = { it.role >= MemberRole.MODERATOR }, onFailure = { false })
    }

    suspend fun hasAdminAccess(chatId: Long, userId: Long): Boolean {
        return getMemberByChatAndUserId(chatId, userId)
            .fold(onSuccess = { it.role == MemberRole.ADMIN }, onFailure = { false })
    }

    private suspend fun checkUsernameExists(username: String): ResultContainer<String> {
        return memberRepository.findByUsername(username)
            .flatMap { member ->
                if (member == null) {
                    ResultContainer.success(username)
                } else {
                    ResultContainer.failure(DuplicateResourceException("Member", username))
                }
            }
    }

}
