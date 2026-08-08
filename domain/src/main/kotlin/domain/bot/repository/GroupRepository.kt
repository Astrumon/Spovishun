package com.ua.astrumon.domain.bot.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Group

interface GroupRepository {
    suspend fun getAllGroups(chatId: Long): ResultContainer<List<Group>>

    suspend fun findGroupByKey(
        chatId: Long,
        key: String,
    ): ResultContainer<Group>

    suspend fun findGroupById(
        chatId: Long,
        groupId: Long,
    ): ResultContainer<Group>

    suspend fun createGroup(
        chatId: Long,
        name: String,
    ): ResultContainer<Group>

    suspend fun deleteGroup(
        chatId: Long,
        key: String,
    ): ResultContainer<Unit>

    suspend fun setReadinessEnabled(
        chatId: Long,
        key: String,
        enabled: Boolean,
    ): ResultContainer<Unit>

    /** A null [icon] clears the group's icon. */
    suspend fun setIcon(
        chatId: Long,
        key: String,
        icon: String?,
    ): ResultContainer<Unit>
}
