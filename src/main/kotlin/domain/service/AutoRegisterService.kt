package com.ua.astrumon.domain.service

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.cache.ChatCache
import com.ua.astrumon.domain.cache.UserCache
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import org.slf4j.LoggerFactory

class AutoRegisterService(
    private val memberService: MemberService,
    private val chatService: ChatService,
    private val userCache: UserCache = UserCache(),
    private val chatCache: ChatCache = ChatCache(),
) {
    private val logger = LoggerFactory.getLogger(AutoRegisterService::class.java)

    suspend fun ensureUserRegistered(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        chatTitle: String? = null,
        chatType: String? = null
    ): ResultContainer<MemberWithChat> {
        if (!chatCache.isKnown(chatId)) {
            chatService.ensureChat(chatId, chatTitle, chatType)
                .fold(
                    onSuccess = { chatCache.markKnown(chatId) },
                    onFailure = { error -> logger.error("Failed to ensure chat registration for chatId: $chatId", error) }
                )
        }

        if (userId == -1L) {
            logger.warn("Attempted to register user with invalid userId: -1, username: $username")
            return ResultContainer.failure(ValidationException("Cannot register user with invalid userId: -1"))
        }

        userCache.get(chatId, username)?.let { cached ->
            logger.debug("Cache hit for user $username in chat $chatId")
            return ResultContainer.success(cached)
        }

        return memberService.getMemberWithChatByUsername(chatId, username)
            .fold(
                onSuccess = { memberWithChat ->
                    logger.debug("User $username already registered in chat $chatId")
                    userCache.put(chatId, username, memberWithChat)
                    ResultContainer.success(memberWithChat)
                },
                onFailure = { error ->
                    if (error is ResourceNotFoundException) {
                        logger.info("Auto-registering user: $username (ID: $userId) in chat: $chatId")
                        memberService.createMember(chatId, userId, username, firstName, role = userRole)
                            .onSuccess { created ->
                                logger.info("Auto-registered user: $username")
                                userCache.put(chatId, username, created)
                            }
                            .onFailure { createError ->
                                logger.error("Failed to auto-register user: $username", createError)
                            }
                    } else {
                        logger.error("Member lookup failed for user: $username", error)
                        ResultContainer.failure(error)
                    }
                }
            )
    }

    suspend fun isUserRegistered(chatId: Long, username: String): Boolean {
        if (userCache.get(chatId, username) != null) return true
        return memberService.getMemberWithChatByUsername(chatId, username).isSuccess
    }
}
