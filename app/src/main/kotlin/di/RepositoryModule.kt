package com.ua.astrumon.di

import com.ua.astrumon.data.admin.repository.ServerHealthRepositoryImpl
import com.ua.astrumon.data.bot.releasenotes.ReleaseNotesRepositoryImpl
import com.ua.astrumon.data.bot.repository.BirthdayGreetingRepositoryImpl
import com.ua.astrumon.data.bot.repository.BotMetaRepositoryImpl
import com.ua.astrumon.data.bot.repository.ChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupMemberRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.MemberRepositoryImpl
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import com.ua.astrumon.domain.bot.repository.BirthdayGreetingRepository
import com.ua.astrumon.domain.bot.repository.BotMetaRepository
import com.ua.astrumon.domain.bot.repository.ChatRepository
import com.ua.astrumon.domain.bot.repository.GroupMemberRepository
import com.ua.astrumon.domain.bot.repository.GroupRepository
import com.ua.astrumon.domain.bot.repository.MemberChatRepository
import com.ua.astrumon.domain.bot.repository.MemberRepository
import com.ua.astrumon.domain.bot.repository.ReleaseNotesRepository
import org.koin.dsl.module

internal val repositoryModule = module {
    single<MemberChatRepository> { MemberChatRepositoryImpl() }
    single<MemberRepository> { MemberRepositoryImpl() }
    single<GroupRepository> { GroupRepositoryImpl() }
    single<GroupMemberRepository> { GroupMemberRepositoryImpl() }
    single<ChatRepository> { ChatRepositoryImpl() }
    single<BirthdayGreetingRepository> { BirthdayGreetingRepositoryImpl() }
    single<BotMetaRepository> { BotMetaRepositoryImpl() }
    single<ReleaseNotesRepository> { ReleaseNotesRepositoryImpl() }
    single<ServerHealthRepository> { ServerHealthRepositoryImpl() }
}
