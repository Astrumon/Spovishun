package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.presentation.bot.BotMessages

/**
 * Turns the outcome of an `/editg` call into the text the user sees (spovishun-180).
 *
 * Split from [GroupSettingsController] on the same line as [GroupParamParser]: parsing decides what
 * was asked, the controller decides what to store, and phrasing is a third job that changes for
 * entirely different reasons — a new wording touches nothing that can lose data.
 *
 * Both listings share [BotMessages.Group.Settings.item] for their rows and differ only in header:
 * an edit lists what it changed, a read lists everything.
 */
object GroupSettingsPresenter {
    fun updated(
        key: String,
        patch: GroupSettingsPatch,
        messages: BotMessages,
    ): String {
        val settings = messages.group.settings
        val rows = buildList {
            (patch.name as? Patch.Value)?.let { add(settings.item(settings.paramName, it.value.escapeHtml())) }
            (patch.icon as? Patch.Value)?.let { add(settings.item(settings.paramIcon, icon(it.value, messages))) }
            (patch.pingMark as? Patch.Value)?.let { add(settings.item(settings.paramMark, mark(it.value, messages))) }
        }
        return (listOf(settings.updatedHeader(key.escapeHtml())) + rows).joinToString("\n")
    }

    fun listing(
        group: GroupWithMembers,
        messages: BotMessages,
    ): String {
        val settings = messages.group.settings
        val rows = listOf(
            settings.item(settings.paramName, group.name.escapeHtml()),
            settings.item(settings.paramIcon, icon(group.icon, messages)),
            settings.item(settings.paramMark, mark(group.pingMark, messages)),
            settings.item(settings.paramReadiness, readiness(group, messages)),
            settings.item(settings.paramMembers, group.members.size.toString()),
        )
        return (listOf(settings.header(group.name.escapeHtml())) + rows).joinToString("\n")
    }

    fun parseFailure(
        failure: GroupParamParseResult.Failure,
        messages: BotMessages,
    ): String {
        val errors = messages.group.paramError
        return when (failure) {
            is GroupParamParseResult.Failure.NotAParameter -> errors.notAParameter(failure.token.escapeHtml())
            is GroupParamParseResult.Failure.MissingSeparator -> errors.missingSeparator(failure.param.flag)
            is GroupParamParseResult.Failure.UnknownParameter ->
                errors.unknown(failure.token.escapeHtml(), GroupParam.supported())
            is GroupParamParseResult.Failure.DuplicateParameter -> errors.duplicate(failure.param.flag)
            is GroupParamParseResult.Failure.EmptyValue -> errors.emptyValue(failure.param.flag)
        }
    }

    private fun icon(
        icon: String?,
        messages: BotMessages,
    ): String = icon?.escapeHtml() ?: messages.group.settings.valueNone

    /**
     * Both emoji branches escape, though only the custom one carries user input. An HTML sink whose
     * safety depends on remembering which branch is developer-controlled is one refactor away from
     * being wrong — the same reasoning as `displayLabelHtml`.
     */
    private fun mark(
        mark: PingMark,
        messages: BotMessages,
    ): String = when (mark) {
        PingMark.Hidden -> messages.group.settings.valueHidden
        PingMark.Default -> messages.group.settings.valueDefault(messages.ping.markGroup.escapeHtml())
        is PingMark.Custom -> mark.emoji.escapeHtml()
    }

    /** Readiness is shown but not editable here, so the value names the command that does edit it. */
    private fun readiness(
        group: GroupWithMembers,
        messages: BotMessages,
    ): String {
        val key = group.name.escapeHtml()
        return if (group.readinessEnabled) {
            messages.group.settings.readinessOn(key)
        } else {
            messages.group.settings.readinessOff(key)
        }
    }
}
