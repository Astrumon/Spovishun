package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer

interface GroupMemberRepository {
    suspend fun addMemberToGroup(chatId: Long, groupKey: String, username: String): ResultContainer<Unit>
    suspend fun removeMemberFromGroup(chatId: Long, groupKey: String, username: String): ResultContainer<Unit>
    suspend fun getGroupMembers(chatId: Long, groupKey: String): ResultContainer<List<String>>
}
