package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `groups with no groups should show empty state message`() = runTest {
        registerMember()
        val update = buildUpdate("/groups")

        showGroupsCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Немає груп") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `groups with existing groups should list them`() = runTest {
        registerMember()
        groupService.createGroup(testChatId, "devs")
        val update = buildUpdate("/groups")

        showGroupsCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("devs") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `newgroup as non-moderator should be denied`() = runTest {
        registerMember(role = MemberRole.MEMBER)
        val update = buildUpdate("/newgroup devs")

        newGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Лише адміни та модератори") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `newgroup as moderator should create group`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        val update = buildUpdate("/newgroup devs")

        newGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("devs") && it.contains("створена") },
                ParseMode.HTML,
            )
        }
        val groups = groupService.getAllGroupsWithMembers(testChatId).getOrThrow()
        assert(groups.any { it.name == "devs" })
    }

    /**
     * Guards the batch read introduced in spovishun-171: the members of several groups now come back
     * from a single query and a group id resolves through one direct query, so every group must still
     * get exactly its own members — an empty one included.
     */
    @Test
    fun `group reads should stay correct for a chat with several groups`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        registerMember(userId = 2L, username = "alice")
        registerMember(userId = 3L, username = "bob")
        groupService.createGroup(testChatId, "devs")
        groupService.createGroup(testChatId, "ops")
        groupService.createGroup(testChatId, "empty")
        groupService.addMemberToGroup(testChatId, "devs", "alice")
        groupService.addMemberToGroup(testChatId, "devs", "bob")
        groupService.addMemberToGroup(testChatId, "ops", "alice")

        val groups = groupService.getAllGroupsWithMembers(testChatId).getOrThrow().associateBy { it.key }

        assertEquals(3, groups.size)
        assertEquals(listOf("alice", "bob"), groups.getValue("devs").members)
        assertEquals(listOf("alice"), groups.getValue("ops").members)
        assertEquals(emptyList(), groups.getValue("empty").members)

        val ops = groupService.getGroupById(testChatId, groups.getValue("ops").id).getOrThrow()
        assertEquals("ops", ops.key)
        assertEquals(listOf("alice"), ops.members)

        showGroupsCommand.execute(bot, buildUpdate("/groups"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("devs") && it.contains("ops") && it.contains("empty") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `delgroup as moderator should delete existing group`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        val update = buildUpdate("/delgroup devs")

        deleteGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("devs") && it.contains("видален") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `delgroup on non-existent group should report error`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        val update = buildUpdate("/delgroup unknown")

        deleteGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("не знайдено") || it.contains("Помилка") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `addtogroup as moderator should add member to group`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        registerMember(userId = 2L, username = "alice")
        groupService.createGroup(testChatId, "devs")
        val update = buildUpdate("/addtogroup devs @alice")

        addUserToGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("alice") && it.contains("додано") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `addtogroup for non-existent group should report group not found`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        val update = buildUpdate("/addtogroup unknown @alice")

        addUserToGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("не знайдено") || it.contains("Помилка") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `removefromgroup as moderator should remove member from group`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        registerMember(userId = 2L, username = "alice")
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", "alice")
        val update = buildUpdate("/removefromgroup devs @alice")

        removeUserFromGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("alice") && it.contains("видален") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `removefromgroup as non-moderator should be denied`() = runTest {
        registerMember(role = MemberRole.MEMBER)
        val update = buildUpdate("/removefromgroup devs @alice")

        removeUserFromGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Лише адміни та модератори") },
                ParseMode.HTML,
            )
        }
    }
}
