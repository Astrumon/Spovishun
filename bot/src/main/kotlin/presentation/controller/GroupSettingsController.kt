package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.EmojiValidator
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
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
    ): CommandResponse = when (val built = buildPatch(values, messages)) {
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
     * Every value is validated before a single one is written — a patch is applied whole or not at
     * all, so a typo in the second parameter must not leave the first one already stored.
     */
    private fun buildPatch(
        values: Map<GroupParam, String>,
        messages: BotMessages,
    ): PatchResult {
        val invalid = values.firstNotNullOfOrNull { (param, raw) -> validationError(param, raw, messages) }
        if (invalid != null) {
            return PatchResult.Invalid(invalid)
        }

        return PatchResult.Built(
            GroupSettingsPatch(
                name = values[GroupParam.NAME].patch { it.lowercase() },
                icon = values[GroupParam.ICON].patch(::iconValue),
                pingMark = values[GroupParam.MARK].patch(::markValue),
            ),
        )
    }

    private fun validationError(
        param: GroupParam,
        raw: String,
        messages: BotMessages,
    ): String? = when (param) {
        GroupParam.NAME -> messages.group.nameInvalid.takeUnless { isValidName(raw) }
        GroupParam.ICON -> messages.group.iconInvalid.takeUnless { raw.isReset(OFF) || EmojiValidator.isSingleEmoji(raw) }
        GroupParam.MARK ->
            messages.group.markInvalid
                .takeUnless { raw.isReset(OFF) || raw.isReset(DEFAULT) || EmojiValidator.isSingleEmoji(raw) }
    }

    /** `$` would make the name unaddressable — the parser would read it back as a parameter. */
    private fun isValidName(raw: String): Boolean = raw.length <= NAME_MAX_LENGTH && !raw.startsWith(GroupParam.PREFIX)

    private fun iconValue(raw: String): String? = raw.takeUnless { it.isReset(OFF) }

    private fun markValue(raw: String): PingMark = when {
        raw.isReset(OFF) -> PingMark.Hidden
        raw.isReset(DEFAULT) -> PingMark.Default
        else -> PingMark.Custom(raw)
    }

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

    private fun <T> String?.patch(transform: (String) -> T): Patch<T> = this?.let { Patch.Value(transform(it)) } ?: Patch.Untouched

    private fun String.isReset(token: String): Boolean = equals(token, ignoreCase = true)

    private sealed interface PatchResult {
        data class Built(
            val patch: GroupSettingsPatch,
        ) : PatchResult

        data class Invalid(
            val message: String,
        ) : PatchResult
    }

    private companion object {
        /** Clears a parameter that has nothing to fall back to, and hides one that does. */
        const val OFF = "off"

        /** Restores a parameter to the value the chat's language declares. */
        const val DEFAULT = "default"

        /** Matches the `groups.name` column width. */
        const val NAME_MAX_LENGTH = 64
    }
}
