package com.ua.astrumon.presentation.bot

import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.bot.model.BotLanguage
import java.text.MessageFormat
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random as KotlinRandom

/**
 * Bot response strings for one language, backed by the `messages` bundle family.
 *
 * One instance per [BotLanguage]; obtain it from [BotMessagesProvider] rather than constructing it
 * — the provider is what knows which language a chat picked.
 *
 * Conventions:
 * - Static keys: computed val (e.g. messages.group.empty)
 * - Parameterized: fun (...) using MessageFormat ({0}, {1}, ...).
 * - Caller is responsible for HTML-escaping dynamic values via String.escapeHtml()
 *   before passing them in. BotMessages does NOT escape.
 * - Literal ' in MessageFormat templates must be doubled ('').
 */
class BotMessages private constructor(
    val language: BotLanguage,
) {
    private val bundle: ResourceBundle = ResourceBundle.getBundle(BASE_NAME, language.locale)

    private fun get(key: String): String = bundle.getString(key)

    private fun format(
        key: String,
        vararg args: Any?,
    ): String = MessageFormat.format(bundle.getString(key), *args)

    val error = Error()
    val success = Success()
    val member = Member()
    val ping = Ping()
    val group = Group()
    val picker = Picker()
    val birthday = Birthday()
    val whatsNew = WhatsNew()
    val random = Random()
    val registration = Registration()
    val welcome = Welcome()
    val languageSetting = LanguageSetting()

    inner class Error {
        fun prefixed(msg: String): String = format("error.prefixed", msg)

        fun accessDenied(reason: String): String = format("error.access_denied", reason)

        val notFound: String get() = get("error.not_found")
        val onlyAdminsModerators: String get() = get("error.only_admins_moderators")
        val onlyAdminsRoles: String get() = get("error.only_admins_roles")

        fun loadMembers(msg: String): String = format("error.load_members", msg)

        fun loadGroups(msg: String): String = format("error.load_groups", msg)

        fun resourceNotFound(
            resource: String,
            identifier: String,
        ): String = format("error.resource_not_found", resource, identifier)

        fun groupNotFound(identifier: String): String = format("error.group_not_found", identifier)

        fun groupNotFoundHtml(
            identifierEscaped: String,
            available: String,
        ): String = format("error.group_not_found_html", identifierEscaped, available)

        fun unknownRole(roleEscaped: String): String = format("error.unknown_role", roleEscaped)

        val loadMembersInternal: String get() = get("error.load_members_internal")
        val loadGroupsInternal: String get() = get("error.load_groups_internal")
    }

    inner class Success {
        val prefix: String get() = get("success.prefix") + " "
        val deletePrefix: String get() = get("success.delete_prefix") + " "

        fun warning(msg: String): String = format("success.warning", msg)
    }

    inner class Member {
        val listHeader: String get() = get("members.list_header")
        val empty: String get() = get("members.empty")

        fun listItem(display: String): String = format("members.list_item", display)

        fun totalSuffix(count: Int): String = format("members.total_suffix", count)
    }

    inner class Ping {
        val readiness = Readiness()

        val markAll: String get() = get("ping.mark.all")

        /** The default a group falls back to; a group may override or hide it (spovishun-180). */
        val markGroup: String get() = get("ping.mark.group")
        val noRegistered: String get() = get("ping.no_registered")
        val noTargets: String get() = get("ping.no_targets")
        val usage: String get() = get("ping.usage")

        fun headerAll(
            marks: String,
            extra: String,
        ): String = if (extra.isEmpty()) {
            format("ping.header.all_no_extra", marks)
        } else {
            format("ping.header.all_with_extra", marks, extra)
        }

        /**
         * [suffix] is marks and free text already joined: a group's marks can be hidden entirely, and
         * a placeholder for an empty string would render as a gap in the header.
         */
        fun headerGroup(
            groupName: String,
            suffix: String,
        ): String = if (suffix.isEmpty()) {
            format("ping.header.group_bare", groupName)
        } else {
            format("ping.header.group", groupName, suffix)
        }

        val menuPrompt: String get() = get("ping.menuPrompt")
        val noGroups: String get() = get("ping.noGroups")
        val allMembersOption: String get() = get("ping.all_members_option")

        inner class Readiness {
            val buttonAccept: String get() = get("ping.readiness.button.accept")
            val buttonDecline: String get() = get("ping.readiness.button.decline")
            val statusAccepted: String get() = get("ping.readiness.status.accepted")
            val statusDeclined: String get() = get("ping.readiness.status.declined")
            val statusPending: String get() = get("ping.readiness.status.pending")
            val usage: String get() = get("ping.readiness.usage")
            val notInvited: String get() = get("ping.readiness.not_invited")
            val sessionClosed: String get() = get("ping.readiness.session_closed")
            val enabled: String get() = get("ping.readiness.enabled")
            val disabled: String get() = get("ping.readiness.disabled")

            fun rosterItem(
                status: String,
                mention: String,
            ): String = format("ping.readiness.roster_item", status, mention)

            fun summary(
                accepted: Int,
                declined: Int,
                pending: Int,
            ): String = format("ping.readiness.summary", accepted, declined, pending)

            fun enabledGroup(groupNameEscaped: String): String = format("ping.readiness.enabled_group", groupNameEscaped)

            fun disabledGroup(groupNameEscaped: String): String = format("ping.readiness.disabled_group", groupNameEscaped)
        }
    }

    inner class Group {
        val settings = Settings()
        val paramError = ParamError()

        val empty: String get() = get("group.empty")
        val listHeader: String get() = get("group.list_header")

        fun listItem(
            name: String,
            key: String,
            members: String,
        ): String = format("group.list_item", name, key, members)

        fun created(nameEscaped: String): String = format("group.created", nameEscaped)

        fun exists(nameEscaped: String): String = format("group.exists", nameEscaped)

        fun deleted(nameEscaped: String): String = format("group.deleted", nameEscaped)

        fun addedTo(
            usersJoined: String,
            groupNameEscaped: String,
        ): String = format("group.added_to", usersJoined, groupNameEscaped)

        fun notAdded(failuresJoined: String): String = format("group.not_added", failuresJoined)

        fun removedFrom(
            usersJoined: String,
            groupNameEscaped: String,
        ): String = format("group.removed_from", usersJoined, groupNameEscaped)

        fun notFoundInGroup(usersJoined: String): String = format("group.not_found_in_group", usersJoined)

        val usageNew: String get() = get("group.usage_new")
        val usageDel: String get() = get("group.usage_del")
        val usageAdd: String get() = get("group.usage_add")
        val usageRemove: String get() = get("group.usage_remove")
        val usageGrant: String get() = get("group.usage_grant")
        val usageEditg: String get() = get("group.usage_editg")

        val iconInvalid: String get() = get("group.icon_invalid")
        val markInvalid: String get() = get("group.mark_invalid")
        val nameInvalid: String get() = get("group.name_invalid")

        /** `/newgroup` takes the name positionally, so `$name=` there is a second name (spovishun-182). */
        val nameParamNotAllowed: String get() = get("group.name_param_not_allowed")

        fun rolesGranted(
            usersJoined: String,
            roleName: String,
        ): String = format("group.roles_granted", usersJoined, roleName)

        fun rolesNotFound(usersJoined: String): String = format("group.roles_not_found", usersJoined)

        val failureNotRegistered: String get() = get("group.failure.not_registered")
        val failureAlreadyIn: String get() = get("group.failure.already_in")
        val failureNotFound: String get() = get("group.failure.not_found")
        val failureError: String get() = get("group.failure.error")
        val failureInvalidUsername: String get() = get("group.failure.invalid_username")

        /**
         * The `/editg` listings (spovishun-180). Editing and reading share the row format and differ
         * only in the header, so one row key serves both — a second one would drift.
         */
        inner class Settings {
            fun updatedHeader(groupNameEscaped: String): String = format("group.updated_header", groupNameEscaped)

            fun header(groupNameEscaped: String): String = format("group.settings_header", groupNameEscaped)

            fun item(
                label: String,
                value: String,
            ): String = format("group.settings_item", label, value)

            val paramName: String get() = get("group.param.name")
            val paramIcon: String get() = get("group.param.icon")
            val paramMark: String get() = get("group.param.mark")
            val paramReadiness: String get() = get("group.param.readiness")
            val paramMembers: String get() = get("group.param.members")

            val valueNone: String get() = get("group.value.none")
            val valueHidden: String get() = get("group.value.hidden")

            fun valueDefault(mark: String): String = format("group.value.default", mark)

            /** Readiness is read-only in `/editg`, so its value names the command that changes it. */
            fun readinessOn(groupKeyEscaped: String): String = format("group.value.readiness_on", groupKeyEscaped)

            fun readinessOff(groupKeyEscaped: String): String = format("group.value.readiness_off", groupKeyEscaped)
        }

        /** Everything the `/editg` parser can reject (spovishun-180). */
        inner class ParamError {
            fun unknown(
                paramEscaped: String,
                supported: String,
            ): String = format("group.unknown_param", paramEscaped, supported)

            fun notAParameter(tokenEscaped: String): String = format("group.not_a_parameter", tokenEscaped)

            /** The pre-spovishun-180 space syntax: name the parameter and show it written correctly. */
            fun missingSeparator(flag: String): String = format("group.missing_separator", flag)

            fun duplicate(flag: String): String = format("group.duplicate_param", flag)

            fun emptyValue(flag: String): String = format("group.empty_value", flag)
        }
    }

    inner class Picker {
        val noMembers: String get() = get("picker.no_members")
        val groupPromptDel: String get() = get("picker.group_prompt.del")
        val groupPromptAddTo: String get() = get("picker.group_prompt.addto")
        val groupPromptRemoveFrom: String get() = get("picker.group_prompt.removefrom")
        val memberPromptAddTo: String get() = get("picker.member_prompt.addto")
        val memberPromptRemoveFrom: String get() = get("picker.member_prompt.removefrom")
        val memberPromptGrant: String get() = get("picker.member_prompt.grant")
        val rolePrompt: String get() = get("picker.role_prompt")
        val confirmButton: String get() = get("picker.delgroup.confirm_button")
        val cancelButton: String get() = get("picker.delgroup.cancel_button")
        val cancelled: String get() = get("picker.delgroup.cancelled")
        val confirmPrompt: String get() = get("picker.delgroup.confirm_prompt")
    }

    inner class Birthday {
        val usage: String get() = get("birthday.usage")
        val invalidDate: String get() = get("birthday.invalid_date")

        fun setSuccess(dateEscaped: String): String = format("birthday.set_success", dateEscaped)

        val cleared: String get() = get("birthday.cleared")

        fun userNotRegistered(usernameEscaped: String): String = format("birthday.user_not_registered", usernameEscaped)

        fun randomGreeting(
            mentionHtml: String,
            random: KotlinRandom = KotlinRandom.Default,
        ): String = format(GREETING_KEYS.random(random), mentionHtml)
    }

    inner class WhatsNew {
        val prefix: String get() = get("whatsnew.prefix") + " "
        val historyTitle: String get() = get("whatsnew.history_title")
        val announcementsEnabled: String get() = get("whatsnew.announcements_enabled")
        val announcementsDisabled: String get() = get("whatsnew.announcements_disabled")
    }

    inner class Random {
        fun result(username: String): String = format("random.result", username)

        val noRegistered: String get() = get("random.no_registered")
        val emptyGroup: String get() = get("random.empty_group")
        val menuPrompt: String get() = get("random.menu_prompt")
        val allMembersOption: String get() = get("random.all_members_option")
        val noGroups: String get() = get("random.no_groups")
    }

    inner class Registration {
        fun failed(firstName: String): String = format("registration.failed", firstName)

        fun alreadyRegistered(firstName: String): String = format("registration.already_registered", firstName)

        fun success(firstName: String): String = format("registration.success", firstName)

        fun successAdmin(firstName: String): String = format("registration.success_admin", firstName)

        val usage: String get() = get("registration.usage")

        fun birthdaySaved(dateEscaped: String): String = format("registration.birthday_saved", dateEscaped)

        val birthdayFailed: String get() = get("registration.birthday_failed")
    }

    inner class Welcome {
        fun message(): String = format("welcome.message", VersionInfo.getFullVersion())

        val invitation: String get() = get("welcome.invitation")
    }

    inner class LanguageSetting {
        val prompt: String get() = get("language.prompt")

        /** Option labels stay in their own language, so a chat on EN still shows "🇺🇦 Українська". */
        fun option(language: BotLanguage): String = get("language.option.${language.code}")

        fun changed(language: BotLanguage): String = format("language.changed", option(language))
    }

    companion object {
        private const val BASE_NAME = "messages"

        private val GREETING_KEYS = (1..5).map { "birthday.greeting.$it" }

        /**
         * Exactly one instance — and therefore one parsed bundle — per language. Instances are
         * interchangeable within a language, so sharing them keeps identity meaningful: two callers
         * that resolved the same language hold the same object.
         */
        private val INSTANCES = ConcurrentHashMap<BotLanguage, BotMessages>()

        fun of(language: BotLanguage): BotMessages = INSTANCES.computeIfAbsent(language) { BotMessages(it) }
    }
}
