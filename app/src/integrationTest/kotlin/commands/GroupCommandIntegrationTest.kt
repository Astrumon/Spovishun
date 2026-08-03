package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

    // --- /editg (spovishun-32) ---

    @Test
    fun `editg as moderator should persist the group icon`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        val update = buildUpdate("/editg devs \$icon 🔥")

        editGroupCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("🔥") },
                ParseMode.HTML,
            )
        }
        assertEquals("🔥", groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }

    @Test
    fun `editg with off should clear a stored icon`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.setIcon(testChatId, "devs", "🔥")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon off"))

        assertNull(groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }

    @Test
    fun `editg should not clobber the readiness flag stored on the same row`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.setReadinessEnabled(testChatId, "devs", false)

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon 🔥"))

        val group = groupService.getGroupByKey(testChatId, "devs").getOrThrow()
        assertEquals("🔥", group.icon)
        assertFalse(group.readinessEnabled)
    }

    @Test
    fun `ping should render the group icon before the group name`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername)
        groupService.setReadinessEnabled(testChatId, "devs", false)
        groupService.setIcon(testChatId, "devs", "🔥")

        pingGroupCommand.execute(bot, buildUpdate("/ping devs"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("🔥 devs") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `editg with a non-emoji value should report a validation error and store nothing`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon abc"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("емодзі") },
                ParseMode.HTML,
            )
        }
        assertNull(groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }

    @Test
    fun `editg with an unknown parameter should list the supported ones`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$nope 1"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("\$icon") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `editg as non-moderator should be denied`() = runTest {
        registerMember(role = MemberRole.MEMBER)
        groupService.createGroup(testChatId, "devs")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon 🔥"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Лише адміни та модератори") },
                ParseMode.HTML,
            )
        }
        assertNull(groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }
}
