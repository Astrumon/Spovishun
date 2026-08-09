package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import org.slf4j.LoggerFactory

/**
 * Backs `/editg <group> [$param=value ...]` — reading and editing a group's parameters
 * (spovishun-32, spovishun-180).
 *
 * Kept apart from [GroupController], which owns creating groups and moving members between them:
 * every parameter added here is a new [GroupParam] entry plus a new `when` branch, and that growth
 * belongs in a class of its own.
 *
 * Two modes, one guard: both reading and editing are moderator-only. Splitting the check per mode
 * would make a mixed command half-authorised, which contradicts applying a patch all-or-nothing.
 */
class GroupSettingsController(
    private val groupService: GroupService,
    memberService: MemberService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    private val logger = LoggerFactory.getLogger(GroupSettingsController::class.java)

    suspend fun editGroup(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        val messages = messagesProvider.forChat(chatId)
        if (args.isEmpty()) {
            return CommandResponse.Error(messages.group.usageEditg)
        }

        val key = args[0].lowercase()
        return when (val parsed = GroupParamParser.parse(args.drop(1))) {
            is GroupParamParseResult.Show -> showSettings(chatId, key, messages)
            is GroupParamParseResult.Edit -> applyEdit(chatId, key, parsed.values, messages)
            is GroupParamParseResult.Failure ->
                CommandResponse.Error(GroupSettingsPresenter.parseFailure(parsed, messages))
        }
    }

    private suspend fun showSettings(
        chatId: Long,
        key: String,
        messages: BotMessages,
    ): CommandResponse = groupService.getGroupByKey(chatId, key).fold(
        onSuccess = { group -> CommandResponse.Success(GroupSettingsPresenter.listing(group, messages)) },
        onFailure = { exception -> failureResponse(exception, key, messages) },
    )

    private suspend fun applyEdit(
        chatId: Long,
        key: String,
        values: Map<GroupParam, String>,
        messages: BotMessages,
    ): CommandResponse = when (val built = GroupParamPatchBuilder.build(values, messages)) {
        is PatchResult.Invalid -> CommandResponse.Error(built.message)
        is PatchResult.Built -> write(chatId, key, built.patch, messages)
    }

    private suspend fun write(
        chatId: Long,
        key: String,
        patch: GroupSettingsPatch,
        messages: BotMessages,
    ): CommandResponse = groupService.updateGroup(chatId, key, patch).fold(
        onSuccess = { CommandResponse.Success(GroupSettingsPresenter.updated(key, patch, messages)) },
        onFailure = { exception ->
            // A duplicate can only be the rename colliding, so the name it collided with is the one
            // worth naming back — the key the caller addressed the group by is not the problem.
            if (exception is DuplicateResourceException) {
                CommandResponse.Error(messages.group.exists(newName(patch, key).escapeHtml()))
            } else {
                failureResponse(exception, key, messages)
            }
        },
    )

    private fun newName(
        patch: GroupSettingsPatch,
        key: String,
    ): String = (patch.name as? Patch.Value)?.value ?: key

    /**
     * A missing group is an ordinary answer; anything else is a defect, and the user sees only a
     * generic message — so it has to leave a trace. Per `security.md` the line carries the exception
     * type alone: neither the group key nor the chat is a safe thing to log.
     */
    private fun failureResponse(
        exception: Throwable,
        key: String,
        messages: BotMessages,
    ): CommandResponse {
        if (exception is ResourceNotFoundException) {
            return CommandResponse.NotFound("Група", key)
        }
        logger.error("Failed to read or update group settings: {}", exception::class.simpleName)
        return CommandResponse.Error(messages.error.loadGroupsInternal)
    }
}
