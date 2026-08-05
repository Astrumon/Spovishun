package com.ua.astrumon.domain.bot.service

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.extension.orFailure
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberChat
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.repository.MemberChatRepository
import com.ua.astrumon.domain.bot.repository.MemberRepository
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
    ): ResultContainer<MemberWithChat> = memberRepository
        .saveOrUpdate(userId, username, firstName)
        .flatMap { member ->
            memberChatRepository
                .existsByMemberIdAndChatId(member.id, chatId)
                .flatMap { exists ->
                    if (exists) {
                        ResultContainer.failure(DuplicateResourceException("Member", username))
                    } else {
                        memberChatRepository
                            .save(member.id, chatId, role, Clock.System.now())
                            .map { memberChat -> member.toMemberWithChat(memberChat) }
                    }
                }
        }

    suspend fun getMemberByUsername(username: String): ResultContainer<Member> = memberRepository
        .findByUsername(username)
        .orFailure { ResourceNotFoundException("Member", username) }

    /**
     * Resolves [usernames] in a single query, keeping the caller's order and silently dropping
     * anyone no longer in the member table — the batch counterpart of [getMemberByUsername], for
     * callers that would otherwise loop and issue one query per name.
     */
    suspend fun getMembersByUsernames(usernames: List<String>): ResultContainer<List<Member>> = memberRepository
        .findAllByUsernames(usernames)
        .map { found ->
            val byUsername = found.associateBy { it.username.lowercase() }
            usernames.mapNotNull { byUsername[it.lowercase()] }
        }

    suspend fun getMemberWithChatByUsername(
        chatId: Long,
        username: String,
    ): ResultContainer<MemberWithChat> = memberRepository
        .findMemberWithChatByChatIdAndUsername(chatId, username)
        .orFailure { ResourceNotFoundException("Member", username) }

    suspend fun getMemberChatByUserId(
        chatId: Long,
        userId: Long,
    ): ResultContainer<MemberChat> = memberRepository
        .findByUserId(userId)
        .orFailure { ResourceNotFoundException("Member", userId.toString()) }
        .flatMap { member ->
            memberChatRepository
                .findByMemberIdAndChatId(member.id, chatId)
                .orFailure { ResourceNotFoundException("Member", userId.toString()) }
        }

    suspend fun updateMemberUsername(
        currentUsername: String,
        newUsername: String,
    ): ResultContainer<Member> = getMemberByUsername(currentUsername)
        .flatMap { member ->
            if (currentUsername == newUsername) {
                ResultContainer.success(member)
            } else {
                memberRepository.saveOrUpdate(member.userId, newUsername, member.firstName)
            }
        }

    suspend fun setMemberRole(
        chatId: Long,
        userId: Long,
        role: MemberRole,
    ): ResultContainer<MemberChat> = getMemberChatByUserId(chatId, userId)
        .flatMap { memberChat ->
            memberChatRepository.updateRole(memberChat.memberId, chatId, role)
        }

    suspend fun getAllMembersInChat(chatId: Long): ResultContainer<List<MemberWithChat>> =
        memberRepository.findAllMembersWithChatByChatId(chatId)

    suspend fun hasModeratorAccess(
        chatId: Long,
        userId: Long,
    ): Boolean = getMemberChatByUserId(chatId, userId)
        .fold(onSuccess = { it.role >= MemberRole.MODERATOR }, onFailure = { false })

    suspend fun hasAdminAccess(
        chatId: Long,
        userId: Long,
    ): Boolean = getMemberChatByUserId(chatId, userId)
        .fold(onSuccess = { it.role == MemberRole.ADMIN }, onFailure = { false })

    private fun Member.toMemberWithChat(memberChat: MemberChat) = MemberWithChat(
        id = id,
        userId = userId,
        username = username,
        firstName = firstName,
        role = memberChat.role,
        joinedAt = memberChat.joinedAt,
    )
}
