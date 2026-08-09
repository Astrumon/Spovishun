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

    /**
     * Creates the group and stores [settings] in the same transaction (spovishun-182).
     *
     * The settings are a parameter rather than a follow-up `updateGroup` call because a group that
     * exists with the wrong parameters is a state `/newgroup` must never be able to produce: the two
     * writes fail together or land together.
     */
    suspend fun createGroup(
        chatId: Long,
        name: String,
        settings: GroupSettingsPatch = GroupSettingsPatch(),
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
