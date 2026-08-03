package com.ua.astrumon.domain.bot.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.repository.GroupMemberRepository
import com.ua.astrumon.domain.bot.repository.GroupRepository

class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {
    suspend fun getAllGroupsWithMembers(chatId: Long): ResultContainer<List<GroupWithMembers>> =
        groupRepository.getAllGroups(chatId).flatMap { groups ->
            val groupsWithMembers = groups.map { group ->
                groupMemberRepository.getGroupMembers(chatId, group.name).map { members ->
                    GroupWithMembers(
                        id = group.id,
                        chatId = chatId,
                        key = group.name,
                        name = group.name,
                        members = members,
                        readinessEnabled = group.readinessEnabled,
                        icon = group.icon,
                    )
                }
            }
            ResultContainer.catching {
                groupsWithMembers.map { it.getOrThrow() }
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
        groupMemberRepository.getGroupMembers(chatId, group.name).map { members ->
            GroupWithMembers(
                id = group.id,
                chatId = chatId,
                key = group.name,
                name = group.name,
                members = members,
                readinessEnabled = group.readinessEnabled,
                icon = group.icon,
            )
        }
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
