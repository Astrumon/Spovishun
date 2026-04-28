package com.ua.astrumon.di

import com.ua.astrumon.data.db.repository.ChatRepositoryImpl
import com.ua.astrumon.data.db.repository.GroupMemberRepositoryImpl
import com.ua.astrumon.data.db.repository.GroupRepositoryImpl
import com.ua.astrumon.data.db.repository.MemberChatRepositoryImpl
import com.ua.astrumon.data.db.repository.MemberRepositoryImpl
import com.ua.astrumon.domain.repository.ChatRepository
import com.ua.astrumon.domain.repository.GroupMemberRepository
import com.ua.astrumon.domain.repository.GroupRepository
import com.ua.astrumon.domain.repository.MemberChatRepository
import com.ua.astrumon.domain.repository.MemberRepository
import org.koin.dsl.module

val repositoryModule = module {
    single<MemberChatRepository> { MemberChatRepositoryImpl() }
    single<MemberRepository> { MemberRepositoryImpl() }
    single<GroupRepository> { GroupRepositoryImpl() }
    single<GroupMemberRepository> { GroupMemberRepositoryImpl() }
    single<ChatRepository> { ChatRepositoryImpl() }
}
