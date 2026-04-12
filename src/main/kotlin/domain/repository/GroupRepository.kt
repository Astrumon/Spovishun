package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Group

interface GroupRepository {
    suspend fun getAllGroups(chatId: Long): ResultContainer<List<Group>>
    suspend fun findGroupByKey(chatId: Long, key: String): ResultContainer<Group>
    suspend fun createGroup(chatId: Long, name: String): ResultContainer<Group>
    suspend fun deleteGroup(chatId: Long, key: String): ResultContainer<Unit>
}
