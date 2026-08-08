package com.ua.astrumon.domain.bot.repository

import com.ua.astrumon.common.result.ResultContainer

interface GroupMemberRepository {
    suspend fun addMemberToGroup(
        chatId: Long,
        groupKey: String,
        username: String,
    ): ResultContainer<Unit>

    suspend fun removeMemberFromGroup(
        chatId: Long,
        groupKey: String,
        username: String,
    ): ResultContainer<Unit>

    /**
     * Usernames of every member of [groupIds], keyed by group id — one round trip for the whole set.
     * A group with no members is absent from the map, so callers fall back to an empty list.
     */
    suspend fun getMembersForGroups(
        chatId: Long,
        groupIds: Collection<Long>,
    ): ResultContainer<Map<Long, List<String>>>
}
