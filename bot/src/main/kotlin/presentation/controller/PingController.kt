package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.util.displayLabel
import com.ua.astrumon.presentation.util.displayLabelHtml
import com.ua.astrumon.presentation.util.toHtmlMention
import org.slf4j.LoggerFactory

class PingController(
    memberService: MemberService,
    private val groupService: GroupService,
    private val chatService: ChatService,
    private val autoRegisterService: AutoRegisterService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    private val logger = LoggerFactory.getLogger(PingController::class.java)

    suspend fun pingAll(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        args: List<String>,
    ): PingOutcome {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)
        val messages = messagesProvider.forChat(chatId)

        val membersResult = memberService.getAllMembersInChat(chatId)
        if (membersResult.isFailure) {
            logger.error("Failed to get all members for pingAll: {}", membersResult.exceptionOrNull()?.let { it::class.simpleName })
            return plain(CommandResponse.Error(messages.error.loadMembersInternal))
        }

        val members = membersResult.getOrNull().orEmpty().map { Member(it.id, it.userId, it.username, it.firstName, it.birthday) }
        if (members.isEmpty()) {
            return plain(CommandResponse.Success(messages.ping.noRegistered))
        }

        val header = messages.ping.headerAll(
            messages.ping.iconAll.repeat(members.size),
            args.joinToString(" ").escapeHtml(),
        )
        return outcome(header, members, readiness = isChatReadinessEnabled(chatId))
    }

    /**
     * Options for the args-less `/ping` picker: the whole-chat option first, then one per group.
     * The whole-chat option is always present, so the menu still renders in a chat with no groups.
     */
    suspend fun groupsForPicker(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
    ): PickerListing {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)
        val messages = messagesProvider.forChat(chatId)

        return groupService.getAllGroupsWithMembers(chatId).fold(
            onSuccess = { groups ->
                val allMembers = PickerOption(ALL_MEMBERS_ID, messages.ping.allMembersOption)
                PickerListing.Show(listOf(allMembers) + groups.map { PickerOption(it.id, it.displayLabel()) })
            },
            onFailure = {
                logger.error("Failed to get groups for groupsForPicker: {}", it::class.simpleName)
                PickerListing.Reject(CommandResponse.Error(messages.error.loadGroupsInternal))
            },
        )
    }

    suspend fun pingGroupById(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        groupId: Long,
    ): PingOutcome {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)
        val messages = messagesProvider.forChat(chatId)

        val allGroups = groupService.getAllGroupsWithMembers(chatId)
        if (allGroups.isFailure) {
            logger.error("Failed to get groups for pingGroupById: {}", allGroups.exceptionOrNull()?.let { it::class.simpleName })
            return plain(CommandResponse.Error(messages.error.loadGroupsInternal))
        }

        val group = allGroups.getOrNull()?.firstOrNull { it.id == groupId }
            ?: return plain(CommandResponse.NotFound("Група", groupId.toString()))

        return pingGroupMembers(messages, group, extra = "")
    }

    suspend fun pingGroup(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
        args: List<String>,
    ): PingOutcome {
        autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)
        val messages = messagesProvider.forChat(chatId)

        if (args.isEmpty()) {
            return plain(CommandResponse.Error(messages.ping.usage))
        }

        val groupKey = args[0].lowercase()
        val group = groupService.getGroupByKey(chatId, groupKey).getOrNull()
            ?: return plain(CommandResponse.NotFound("Група", groupKey, availableGroupKeys(chatId)))

        return pingGroupMembers(messages, group, extra = args.drop(1).joinToString(" "))
    }

    /** Turns readiness mode on or off for a single group. Moderator-only, like every group edit. */
    suspend fun setGroupReadiness(
        chatId: Long,
        userId: Long,
        groupKey: String,
        enabled: Boolean,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        val messages = messagesProvider.forChat(chatId)
        val key = groupKey.lowercase()
        return groupService.setReadinessEnabled(chatId, key, enabled).fold(
            onSuccess = { CommandResponse.Success(groupToggleMessage(messages, key, enabled)) },
            onFailure = { exception -> groupToggleFailure(chatId, key, exception) },
        )
    }

    private fun groupToggleMessage(
        messages: BotMessages,
        key: String,
        enabled: Boolean,
    ): String {
        val name = key.escapeHtml()
        return if (enabled) {
            messages.ping.readiness.enabledGroup(name)
        } else {
            messages.ping.readiness.disabledGroup(name)
        }
    }

    private suspend fun groupToggleFailure(
        chatId: Long,
        key: String,
        exception: BaseException,
    ): CommandResponse {
        val messages = messagesProvider.forChat(chatId)
        if (exception is ResourceNotFoundException) {
            return CommandResponse.NotFound("Група", key, availableGroupKeys(chatId))
        }
        logger.error("Failed to set group readiness: {}", exception::class.simpleName)
        return CommandResponse.Error(messages.error.loadGroupsInternal)
    }

    /** Turns readiness mode on or off for the whole-chat ping (`/all`). Moderator-only. */
    suspend fun setChatReadiness(
        chatId: Long,
        userId: Long,
        enabled: Boolean,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        val messages = messagesProvider.forChat(chatId)
        return chatService.setReadinessEnabled(chatId, enabled).fold(
            onSuccess = {
                val message = if (enabled) messages.ping.readiness.enabled else messages.ping.readiness.disabled
                CommandResponse.Success(message)
            },
            onFailure = { exception ->
                logger.error("Failed to set chat readiness: {}", exception::class.simpleName)
                CommandResponse.Error(messages.error.loadMembersInternal)
            },
        )
    }

    private suspend fun pingGroupMembers(
        messages: BotMessages,
        group: GroupWithMembers,
        extra: String,
    ): PingOutcome {
        val members = resolveMembers(group)
        if (members.isEmpty()) {
            return plain(CommandResponse.Success(messages.ping.noTargets))
        }

        val icons = messages.ping.iconGroup.repeat(members.size)
        // The header is rendered with ParseMode.HTML and BotMessages never escapes — a stray `<`
        // makes Telegram reject the whole send. The moderator-chosen group name is neutralised by
        // displayLabelHtml, the caller's free text has to be neutralised here.
        val header = messages.ping.headerGroup(group.displayLabelHtml(), icons, extra.escapeHtml())
        return outcome(header, members, readiness = group.readinessEnabled)
    }

    /**
     * Group membership is stored by username; anyone no longer in the member table is dropped.
     * Resolved in one batch query — a per-username lookup made `/ping` cost one round trip per member.
     */
    private suspend fun resolveMembers(group: GroupWithMembers): List<Member> {
        val members = memberService.getMembersByUsernames(group.members).getOrNull().orEmpty()
        if (members.size != group.members.size) {
            logger.warn("{} member(s) of group id '{}' are not in the member database", group.members.size - members.size, group.id)
        }
        return members
    }

    private suspend fun availableGroupKeys(chatId: Long): List<String> =
        groupService.getAllGroupsWithMembers(chatId).getOrNull()?.map { it.key } ?: emptyList()

    /** Readiness is the default; a failed lookup must not silently downgrade the mode. */
    private suspend fun isChatReadinessEnabled(chatId: Long): Boolean = chatService.isReadinessEnabled(chatId).fold(
        onSuccess = { it },
        onFailure = {
            logger.warn("Failed to read chat readiness flag, defaulting to enabled: {}", it::class.simpleName)
            true
        },
    )

    private fun outcome(
        header: String,
        members: List<Member>,
        readiness: Boolean,
    ): PingOutcome = if (readiness) {
        PingOutcome.Readiness(header, members)
    } else {
        PingOutcome.Plain(CommandResponse.Success("$header\n\n${members.joinToString(" ") { it.toHtmlMention() }}"))
    }

    private fun plain(response: CommandResponse): PingOutcome = PingOutcome.Plain(response)

    companion object {
        /** Sentinel picker-option id for "the whole chat" — group ids are DB serials and start at 1. */
        const val ALL_MEMBERS_ID = 0L
    }
}
