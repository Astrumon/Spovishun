package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.domain.model.MemberRole
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GroupCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `groups with no groups should show empty state message`() =
        runTest {
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
    fun `groups with existing groups should list them`() =
        runTest {
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
    fun `newgroup as non-moderator should be denied`() =
        runTest {
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
    fun `newgroup as moderator should create group`() =
        runTest {
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
    fun `delgroup as moderator should delete existing group`() =
        runTest {
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
    fun `delgroup on non-existent group should report error`() =
        runTest {
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
    fun `addtogroup as moderator should add member to group`() =
        runTest {
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
    fun `addtogroup for non-existent group should report group not found`() =
        runTest {
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
    fun `removefromgroup as moderator should remove member from group`() =
        runTest {
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
    fun `removefromgroup as non-moderator should be denied`() =
        runTest {
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
