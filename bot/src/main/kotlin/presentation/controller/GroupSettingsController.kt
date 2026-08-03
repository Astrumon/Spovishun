package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.EmojiValidator
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider

/**
 * Backs `/editg <group> $param <value>` — editing a group's parameters (spovishun-32).
 *
 * Kept apart from [GroupController], which owns creating groups and moving members between them:
 * every parameter added here is a new [GroupParam] entry plus a new `when` branch, and that growth
 * belongs in a class of its own.
 */
class GroupSettingsController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    suspend fun editGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        val messages = messagesProvider.forChat(chatId)
        if (args.size < ARGS_WITH_PARAM_VALUE) {
            return CommandResponse.Error(messages.group.usageEditg)
        }

        val paramToken = args[1]
        val param = GroupParam.from(paramToken)
            ?: return CommandResponse.Error(messages.group.unknownParam(paramToken.escapeHtml(), GroupParam.supported()))

        val value = args.drop(ARGS_WITH_PARAM_VALUE - 1).joinToString(" ")
        return when (param) {
            GroupParam.ICON -> setIcon(chatId, args[0].lowercase(), value, messages)
        }
    }

    private suspend fun setIcon(
        chatId: Long,
        key: String,
        value: String,
        messages: BotMessages,
    ): CommandResponse {
        val isReset = value.equals(RESET_VALUE, ignoreCase = true)
        if (!isReset && !EmojiValidator.isSingleEmoji(value)) {
            return CommandResponse.Error(messages.group.iconInvalid)
        }

        val icon = value.takeUnless { isReset }
        return groupService.setIcon(chatId, key, icon).fold(
            onSuccess = {
                val name = key.escapeHtml()
                CommandResponse.Success(
                    if (icon == null) messages.group.iconCleared(name) else messages.group.iconSet(name, icon),
                )
            },
            onFailure = { exception ->
                when (exception) {
                    is ResourceNotFoundException -> CommandResponse.NotFound("Група", key)
                    else -> CommandResponse.Error(exception.userMessage)
                }
            },
        )
    }

    private companion object {
        /** `<group> $param <value>` — anything shorter is a usage error. */
        const val ARGS_WITH_PARAM_VALUE = 3

        /** Clears a parameter instead of setting it: `/editg devs $icon off`. */
        const val RESET_VALUE = "off"
    }
}

/** Parameters `/editg` understands. One entry per editable group setting. */
private enum class GroupParam(
    val flag: String,
) {
    ICON("\$icon"),
    ;

    companion object {
        fun from(token: String): GroupParam? = entries.firstOrNull { it.flag.equals(token, ignoreCase = true) }

        fun supported(): String = entries.joinToString(", ") { it.flag }
    }
}
