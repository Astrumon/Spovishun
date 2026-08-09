package com.ua.astrumon.domain.bot.service

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.cache.ChatCache
import com.ua.astrumon.domain.bot.cache.UserCache
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import org.slf4j.LoggerFactory

class AutoRegisterService(
    private val memberService: MemberService,
    private val chatService: ChatService,
    private val userCache: UserCache,
    private val chatCache: ChatCache,
) {
    private val logger = LoggerFactory.getLogger(AutoRegisterService::class.java)

    /** Bundles the member identity so the private helpers below stay within the parameter limit. */
    private data class RegistrationRequest(
        val chatId: Long,
        val userId: Long,
        val username: String,
        val firstName: String,
        val resolveRole: suspend () -> MemberRole,
    )

    /**
     * [resolveRole] is a supplier rather than a value because the role is only needed to *create* a
     * member, and the common case returns from the cache well before that. Callers derive the role
     * from a blocking Telegram round trip; passing it eagerly made every already-known user pay for
     * one (spovishun-172).
     */
    suspend fun ensureUserRegistered(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        resolveRole: suspend () -> MemberRole,
        chatTitle: String? = null,
        chatType: String? = null,
    ): ResultContainer<MemberWithChat> {
        ensureChatKnown(chatId, chatTitle, chatType)

        if (userId == INVALID_USER_ID) {
            logger.warn("Attempted to register user with invalid userId")
            return ResultContainer.failure(
                ValidationException("Cannot register user with invalid userId: $INVALID_USER_ID"),
            )
        }

        userCache.get(chatId, username)?.let { cached ->
            logger.debug("Cache hit for user")
            return ResultContainer.success(cached)
        }

        return resolveMember(RegistrationRequest(chatId, userId, username, firstName, resolveRole))
    }

    /**
     * Reports whether the user is already known in the chat.
     * A missing member resolves to `false`; any other lookup error is propagated so callers can
     * distinguish "not registered" from "lookup unavailable".
     */
    suspend fun isUserRegistered(
        chatId: Long,
        username: String,
    ): ResultContainer<Boolean> {
        if (userCache.get(chatId, username) != null) return ResultContainer.success(true)
        return memberService
            .getMemberWithChatByUsername(chatId, username)
            .fold(
                onSuccess = { ResultContainer.success(true) },
                onFailure = { error ->
                    if (error is ResourceNotFoundException) {
                        ResultContainer.success(false)
                    } else {
                        logger.error("Member lookup failed: ${error::class.simpleName}")
                        ResultContainer.failure(error)
                    }
                },
            )
    }

    private suspend fun ensureChatKnown(
        chatId: Long,
        chatTitle: String?,
        chatType: String?,
    ) {
        if (chatCache.isKnown(chatId)) return
        chatService
            .ensureChat(chatId, chatTitle, chatType)
            .fold(
                onSuccess = { chatCache.markKnown(chatId) },
                onFailure = { error -> logger.error("Failed to ensure chat registration: ${error::class.simpleName}") },
            )
    }

    private suspend fun resolveMember(request: RegistrationRequest): ResultContainer<MemberWithChat> = memberService
        .getMemberWithChatByUsername(request.chatId, request.username)
        .fold(
            onSuccess = { memberWithChat ->
                logger.debug("User already registered")
                userCache.put(request.chatId, request.username, memberWithChat)
                ResultContainer.success(memberWithChat)
            },
            onFailure = { error ->
                if (error is ResourceNotFoundException) {
                    createMember(request)
                } else {
                    logger.error("Member lookup failed: ${error::class.simpleName}")
                    ResultContainer.failure(error)
                }
            },
        )

    private suspend fun createMember(request: RegistrationRequest): ResultContainer<MemberWithChat> {
        logger.info("Auto-registering new user")
        return memberService
            .createMember(
                request.chatId,
                request.userId,
                request.username,
                request.firstName,
                role = request.resolveRole(),
            ).onSuccess { created ->
                logger.info("Auto-registered user successfully")
                userCache.put(request.chatId, request.username, created)
            }.onFailure { createError ->
                logger.error("Failed to auto-register user: ${createError::class.simpleName}")
            }
    }

    private companion object {
        const val INVALID_USER_ID = -1L
    }
}
