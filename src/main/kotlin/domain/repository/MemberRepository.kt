package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.BirthDate
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberWithChat

interface MemberRepository {
    suspend fun findById(id: Long): ResultContainer<Member?>
    suspend fun findByUserId(userId: Long): ResultContainer<Member?>
    suspend fun findByUsername(username: String): ResultContainer<Member?>
    suspend fun saveOrUpdate(userId: Long, username: String, firstName: String): ResultContainer<Member>
    suspend fun findMemberWithChatByChatIdAndUsername(chatId: Long, username: String): ResultContainer<MemberWithChat?>
    suspend fun findAllMembersWithChatByChatId(chatId: Long): ResultContainer<List<MemberWithChat>>
    suspend fun findAllByBirthMd(birthMd: Int): ResultContainer<List<Member>>
    suspend fun updateBirthday(userId: Long, birthday: BirthDate?): ResultContainer<Member>
}
