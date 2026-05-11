package com.ua.astrumon.presentation.bot

import com.ua.astrumon.common.util.VersionInfo
import java.text.MessageFormat
import java.util.ResourceBundle
import kotlin.random.Random

/**
 * Centralized bot response strings, backed by messages.properties.
 *
 * Conventions:
 * - Static keys: computed val (e.g. BotMessages.Group.empty)
 * - Parameterized: fun (...) using MessageFormat ({0}, {1}, ...).
 * - Caller is responsible for HTML-escaping dynamic values via String.escapeHtml()
 *   before passing them in. BotMessages does NOT escape.
 * - Literal ' in MessageFormat templates must be doubled (''). Currently no apostrophes
 *   in Ukrainian copy; document if added.
 */
object BotMessages {
    private val bundle: ResourceBundle = ResourceBundle.getBundle("messages")

    private fun get(key: String): String = bundle.getString(key)
    private fun format(key: String, vararg args: Any?): String =
        MessageFormat.format(bundle.getString(key), *args)

    object Error {
        fun prefixed(msg: String): String = format("error.prefixed", msg)
        fun accessDenied(reason: String): String = format("error.access_denied", reason)
        val notFound: String get() = get("error.not_found")
        val onlyAdminsModerators: String get() = get("error.only_admins_moderators")
        val onlyAdminsRoles: String get() = get("error.only_admins_roles")
        fun loadMembers(msg: String): String = format("error.load_members", msg)
        fun loadGroups(msg: String): String = format("error.load_groups", msg)
        fun resourceNotFound(resource: String, identifier: String): String =
            format("error.resource_not_found", resource, identifier)
        fun groupNotFound(identifier: String): String = format("error.group_not_found", identifier)
        fun groupNotFoundHtml(identifierEscaped: String, available: String): String =
            format("error.group_not_found_html", identifierEscaped, available)
        fun unknownRole(roleEscaped: String): String = format("error.unknown_role", roleEscaped)
        val loadMembersInternal: String get() = get("error.load_members_internal")
        val loadGroupsInternal: String get() = get("error.load_groups_internal")
    }

    object Success {
        val prefix: String get() = get("success.prefix") + " "
        val deletePrefix: String get() = get("success.delete_prefix") + " "
        fun warning(msg: String): String = format("success.warning", msg)
    }

    object Member {
        val listHeader: String get() = get("members.list_header")
        val empty: String get() = get("members.empty")
        fun listItem(display: String): String = format("members.list_item", display)
        fun totalSuffix(count: Int): String = format("members.total_suffix", count)
    }

    object Ping {
        val iconAll: String get() = get("ping.icon.all")
        val iconGroup: String get() = get("ping.icon.group")
        val noRegistered: String get() = get("ping.no_registered")
        val noTargets: String get() = get("ping.no_targets")
        val usage: String get() = get("ping.usage")
        fun headerAll(crabs: String, extra: String): String =
            if (extra.isEmpty()) format("ping.header.all_no_extra", crabs)
            else format("ping.header.all_with_extra", crabs, extra)
        fun headerGroup(groupName: String, crabs: String, extra: String): String =
            if (extra.isEmpty()) format("ping.header.group_no_extra", groupName, crabs)
            else format("ping.header.group_with_extra", groupName, crabs, extra)
        val menuPrompt: String get() = get("ping.menuPrompt")
        val noGroups: String get() = get("ping.noGroups")
    }

    object Group {
        val empty: String get() = get("group.empty")
        val listHeader: String get() = get("group.list_header")
        fun listItem(name: String, key: String, members: String): String =
            format("group.list_item", name, key, members)
        fun created(nameEscaped: String): String = format("group.created", nameEscaped)
        fun exists(nameEscaped: String): String = format("group.exists", nameEscaped)
        fun deleted(nameEscaped: String): String = format("group.deleted", nameEscaped)
        fun addedTo(usersJoined: String, groupNameEscaped: String): String =
            format("group.added_to", usersJoined, groupNameEscaped)
        fun notAdded(failuresJoined: String): String = format("group.not_added", failuresJoined)
        fun removedFrom(usersJoined: String, groupNameEscaped: String): String =
            format("group.removed_from", usersJoined, groupNameEscaped)
        fun notFoundInGroup(usersJoined: String): String = format("group.not_found_in_group", usersJoined)
        val usageNew: String get() = get("group.usage_new")
        val usageDel: String get() = get("group.usage_del")
        val usageAdd: String get() = get("group.usage_add")
        val usageRemove: String get() = get("group.usage_remove")
        val usageGrant: String get() = get("group.usage_grant")
        fun rolesGranted(usersJoined: String, roleName: String): String =
            format("group.roles_granted", usersJoined, roleName)
        fun rolesNotFound(usersJoined: String): String = format("group.roles_not_found", usersJoined)
        val failureNotRegistered: String get() = get("group.failure.not_registered")
        val failureAlreadyIn: String get() = get("group.failure.already_in")
        val failureNotFound: String get() = get("group.failure.not_found")
        val failureError: String get() = get("group.failure.error")
        val failureInvalidUsername: String get() = get("group.failure.invalid_username")
    }

    object Birthday {
        val usage: String get() = get("birthday.usage")
        val invalidDate: String get() = get("birthday.invalid_date")
        fun setSuccess(dateEscaped: String): String = format("birthday.set_success", dateEscaped)
        val cleared: String get() = get("birthday.cleared")
        fun userNotRegistered(usernameEscaped: String): String = format("birthday.user_not_registered", usernameEscaped)

        private val greetingKeys = (1..5).map { "birthday.greeting.$it" }
        fun randomGreeting(firstNameEscaped: String, random: Random = Random.Default): String =
            format(greetingKeys.random(random), firstNameEscaped)
    }

    object WhatsNew {
        val prefix: String get() = get("whatsnew.prefix") + " "
        val historyTitle: String get() = get("whatsnew.history_title")
        val noNotes: String get() = get("whatsnew.no_notes")
    }

    object Registration {
        fun failed(firstName: String): String = format("registration.failed", firstName)
        fun alreadyRegistered(firstName: String): String = format("registration.already_registered", firstName)
        fun success(firstName: String): String = format("registration.success", firstName)
        fun successAdmin(firstName: String): String = format("registration.success_admin", firstName)
    }

    object Welcome {
        fun message(): String = format("welcome.message", VersionInfo.getFullVersion())
        val invitation: String get() = get("welcome.invitation")
    }
}
