package com.ua.astrumon.domain.bot.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.repository.GroupMemberRepository
import com.ua.astrumon.domain.bot.repository.GroupRepository

class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {
    /** Two queries regardless of how many groups the chat has — never one per group (spovishun-171). */
    suspend fun getAllGroupsWithMembers(chatId: Long): ResultContainer<List<GroupWithMembers>> =
        groupRepository.getAllGroups(chatId).flatMap { groups ->
            groupMemberRepository.getMembersForGroups(chatId, groups.map { it.id }).map { membersByGroup ->
                groups.map { group -> group.withMembers(membersByGroup[group.id].orEmpty()) }
            }
        }

    suspend fun createGroup(
        chatId: Long,
        name: String,
    ): ResultContainer<Group> = groupRepository.createGroup(chatId, name)

    suspend fun deleteGroup(
        chatId: Long,
        key: String,
    ): ResultContainer<Unit> = groupRepository.deleteGroup(chatId, key)

    suspend fun addMemberToGroup(
        chatId: Long,
        key: String,
        username: String,
    ): ResultContainer<Unit> = groupMemberRepository.addMemberToGroup(chatId, key, username)

    suspend fun removeMemberFromGroup(
        chatId: Long,
        key: String,
        username: String,
    ): ResultContainer<Unit> = groupMemberRepository.removeMemberFromGroup(chatId, key, username)

    suspend fun getGroupByKey(
        chatId: Long,
        key: String,
    ): ResultContainer<GroupWithMembers> = groupRepository.findGroupByKey(chatId, key).flatMap { group ->
        loadMembers(chatId, group)
    }

    /**
     * Resolves a single group by id. The inline pickers hand back an id, and every caller used to
     * scan the whole chat to translate it back into a group (spovishun-171).
     */
    suspend fun getGroupById(
        chatId: Long,
        groupId: Long,
    ): ResultContainer<GroupWithMembers> = groupRepository.findGroupById(chatId, groupId).flatMap { group ->
        loadMembers(chatId, group)
    }

    suspend fun setReadinessEnabled(
        chatId: Long,
        key: String,
        enabled: Boolean,
    ): ResultContainer<Unit> = groupRepository.setReadinessEnabled(chatId, key, enabled)

    /** A null [icon] clears the group's icon. */
    suspend fun setIcon(
        chatId: Long,
        key: String,
        icon: String?,
    ): ResultContainer<Unit> = groupRepository.setIcon(chatId, key, icon)

    private suspend fun loadMembers(
        chatId: Long,
        group: Group,
    ): ResultContainer<GroupWithMembers> = groupMemberRepository
        .getMembersForGroups(chatId, listOf(group.id))
        .map { membersByGroup -> group.withMembers(membersByGroup[group.id].orEmpty()) }

    /** A group's key and its display name are the same value — this is the single place that says so. */
    private fun Group.withMembers(members: List<String>) = GroupWithMembers(
        id = id,
        chatId = chatId,
        key = name,
        name = name,
        members = members,
        readinessEnabled = readinessEnabled,
        icon = icon,
    )
}

data class GroupWithMembers(
    val id: Long,
    val chatId: Long,
    val key: String,
    val name: String,
    val members: List<String>,
    val readinessEnabled: Boolean = true,
    val icon: String? = null,
)
