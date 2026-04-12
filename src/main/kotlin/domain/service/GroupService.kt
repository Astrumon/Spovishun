package com.ua.astrumon.domain.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Group
import com.ua.astrumon.domain.repository.GroupMemberRepository
import com.ua.astrumon.domain.repository.GroupRepository
import org.slf4j.LoggerFactory

class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository
) {
    private val logger = LoggerFactory.getLogger(GroupService::class.java)

    suspend fun getAllGroupsWithMembers(chatId: Long): ResultContainer<List<GroupWithMembers>> {
        return groupRepository.getAllGroups(chatId).flatMap { groups ->
            val groupsWithMembers = groups.map { group ->
                groupMemberRepository.getGroupMembers(chatId, group.name).map { members ->
                    GroupWithMembers(
                        id = group.id,
                        chatId = chatId,
                        key = group.name,
                        name = group.name,
                        members = members
                    )
                }
            }
            ResultContainer.catching {
                groupsWithMembers.map { it.getOrThrow() }
            }
        }
    }

    suspend fun createGroup(chatId: Long, name: String): ResultContainer<Group> {
        return groupRepository.createGroup(chatId, name)
    }

    suspend fun deleteGroup(chatId: Long, key: String): ResultContainer<Unit> {
        return groupRepository.deleteGroup(chatId, key)
    }

    suspend fun addMemberToGroup(chatId: Long, key: String, username: String): ResultContainer<Unit> {
        return groupMemberRepository.addMemberToGroup(chatId, key, username)
    }

    suspend fun removeMemberFromGroup(chatId: Long, key: String, username: String): ResultContainer<Unit> {
        return groupMemberRepository.removeMemberFromGroup(chatId, key, username)
    }

    suspend fun getGroupByKey(chatId: Long, key: String): ResultContainer<GroupWithMembers> {
        logger.info("getGroupByKey called with chatId=$chatId, key='$key'")
        return groupRepository.findGroupByKey(chatId, key).flatMap { group ->
            groupMemberRepository.getGroupMembers(chatId, group.name).map { members ->
                GroupWithMembers(
                    id = group.id,
                    chatId = chatId,
                    key = group.name,
                    name = group.name,
                    members = members
                )
            }
        }
    }
}

data class GroupWithMembers(
    val id: Long,
    val chatId: Long,
    val key: String,
    val name: String,
    val members: List<String>
)
