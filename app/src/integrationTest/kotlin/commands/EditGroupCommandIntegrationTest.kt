package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
import com.ua.astrumon.presentation.bot.BotMessages
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `/editg` end to end over a real PostgreSQL (spovishun-32, spovishun-180) — the multi-parameter
 * syntax, the three ping-mark states, renaming, and how `/ping` renders what this command stored.
 *
 * Split out of `GroupCommandIntegrationTest` when the parameter set grew: the two classes share a
 * base but not a subject, and one of them was carrying half the file.
 */
class EditGroupCommandIntegrationTest : BaseIntegrationTest() {
    /**
     * Read from the bundle, never hardcoded: the mark tests prove a custom emoji *replaces* the
     * default, and that proof evaporates the moment someone edits the default to whatever the test
     * happens to use as its custom value — which is exactly what a 🦞 → 🦀 change already did once.
     */
    private val defaultMark = BotMessages.of(BotLanguage.UK).ping.markGroup

    private suspend fun setIcon(
        key: String,
        icon: String?,
    ) = groupService.updateGroup(testChatId, key, GroupSettingsPatch(icon = Patch.Value(icon)))

    private suspend fun setMark(
        key: String,
        mark: PingMark,
    ) = groupService.updateGroup(testChatId, key, GroupSettingsPatch(pingMark = Patch.Value(mark)))

    @Test
    fun `editg as moderator should persist the group icon`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        val update = buildUpdate("/editg devs \$icon=🔥")

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
        setIcon("devs", "🔥")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon=off"))

        assertNull(groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }

    @Test
    fun `editg should apply several parameters in one call`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon=🔥 \$mark=🦀 \$name=devops"))

        val group = groupService.getGroupByKey(testChatId, "devops").getOrThrow()
        assertEquals("🔥", group.icon)
        assertEquals(PingMark.Custom("🦀"), group.pingMark)
        assertTrue(groupService.getGroupByKey(testChatId, "devs").isFailure)
    }

    @Test
    fun `editg should rename a group without losing its members`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername)

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$name=devops"))

        assertEquals(listOf(testUsername), groupService.getGroupByKey(testChatId, "devops").getOrThrow().members)
    }

    @Test
    fun `editg should refuse a rename onto an existing group and change nothing`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.createGroup(testChatId, "ops")
        setIcon("ops", "🎯")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$name=ops \$icon=🔥"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("ops") && it.contains("вже існує") },
                ParseMode.HTML,
            )
        }
        // The whole patch rolls back: the group keeps its name AND never gets the icon from the
        // same command — and the group it collided with is untouched.
        val devs = groupService.getGroupByKey(testChatId, "devs").getOrThrow()
        assertNull(devs.icon)
        assertEquals("🎯", groupService.getGroupByKey(testChatId, "ops").getOrThrow().icon)
    }

    @Test
    fun `editg should not clobber the readiness flag stored on the same row`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.setReadinessEnabled(testChatId, "devs", false)

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon=🔥"))

        val group = groupService.getGroupByKey(testChatId, "devs").getOrThrow()
        assertEquals("🔥", group.icon)
        assertFalse(group.readinessEnabled)
    }

    @Test
    fun `editg without parameters should show the current settings`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername)
        setIcon("devs", "🔥")

        editGroupCommand.execute(bot, buildUpdate("/editg devs"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("🔥") && it.contains("devs") && it.contains("1") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `ping should render the group icon before the group name`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername)
        groupService.setReadinessEnabled(testChatId, "devs", false)
        setIcon("devs", "🔥")

        pingGroupCommand.execute(bot, buildUpdate("/ping devs"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("🔥 devs") },
                ParseMode.HTML,
            )
        }
    }

    /**
     * The two tests below distinguish "custom mark rendered" from "default rendered" by comparing
     * emoji. If the two are ever the same character, both keep passing while proving nothing — so
     * fail here instead, loudly, at the one assumption they rest on.
     */
    @Test
    fun `the custom mark used by these tests must differ from the default`() {
        assertFalse(CUSTOM_MARK.emoji == defaultMark, "pick a CUSTOM_MARK that is not the default $defaultMark")
    }

    @Test
    fun `ping should repeat a custom mark once per member`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername)
        groupService.setReadinessEnabled(testChatId, "devs", false)
        setMark("devs", CUSTOM_MARK)

        pingGroupCommand.execute(bot, buildUpdate("/ping devs"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains(CUSTOM_MARK.emoji) && !it.contains(defaultMark) },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `ping should render no mark at all when it is hidden`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")
        groupService.addMemberToGroup(testChatId, "devs", testUsername)
        groupService.setReadinessEnabled(testChatId, "devs", false)
        setMark("devs", CUSTOM_MARK)
        setMark("devs", PingMark.Hidden)

        pingGroupCommand.execute(bot, buildUpdate("/ping devs"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { !it.contains(defaultMark) && !it.contains(CUSTOM_MARK.emoji) && !it.contains("  ") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `editg with a non-emoji value should report a validation error and store nothing`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon=abc"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("емодзі") },
                ParseMode.HTML,
            )
        }
        assertNull(groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }

    /** The old space syntax is gone; the reply has to teach the new one rather than shrug. */
    @Test
    fun `editg with the old space syntax should point at the equals form`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon 🔥"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("\$icon=") },
                ParseMode.HTML,
            )
        }
        assertNull(groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }

    @Test
    fun `editg with an unknown parameter should list the supported ones`() = runTest {
        registerMember(role = MemberRole.MODERATOR)
        groupService.createGroup(testChatId, "devs")

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$nope=1"))

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

        editGroupCommand.execute(bot, buildUpdate("/editg devs \$icon=🔥"))

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Лише адміни та модератори") },
                ParseMode.HTML,
            )
        }
        assertNull(groupService.getGroupByKey(testChatId, "devs").getOrThrow().icon)
    }

    private companion object {
        /** Deliberately not the bundle default — see the guard test above. */
        val CUSTOM_MARK = PingMark.Custom("⚡")
    }
}
