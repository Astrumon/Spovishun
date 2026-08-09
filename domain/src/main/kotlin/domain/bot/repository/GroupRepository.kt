package com.ua.astrumon.domain.bot.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch

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

    /**
     * Applies every field [patch] states, and only those, in one transaction (spovishun-180).
     *
     * Fails with `DuplicateResourceException` when the patch renames the group onto a name the chat
     * already uses; renaming a group to the name it already has succeeds and changes nothing.
     */
    suspend fun updateGroup(
        chatId: Long,
        key: String,
        patch: GroupSettingsPatch,
    ): ResultContainer<Unit>
}
