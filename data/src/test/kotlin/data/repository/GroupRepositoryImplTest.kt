package data.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.bot.repository.ChatRepositoryImpl
import com.ua.astrumon.data.bot.repository.GroupRepositoryImpl
import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.GroupSettings
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
import data.db.H2TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupRepositoryImplTest {
    private val repository = GroupRepositoryImpl()
    private val chatRepository = ChatRepositoryImpl()

    @BeforeTest
    fun setup() {
        H2TestDatabaseFactory.initialize()
        transaction {
            GroupMembers.deleteAll()
            GroupSettings.deleteAll()
            Groups.deleteAll()
            Chats.deleteAll()
        }
    }

    private suspend fun ensureChat(chatId: Long) {
        chatRepository.save(chatId, null, null)
    }

    @Test
    fun `createGroup should create and return group`() = runTest {
        ensureChat(100L)
        val result = repository.createGroup(100L, "devs")

        assertTrue(result.isSuccess)
        val group = result.getOrThrow()
        assertEquals(100L, group.chatId)
        assertEquals("devs", group.name)
        assertTrue(group.memberUsernames.isEmpty())
    }

    /**
     * The settings land in the same transaction as the group (spovishun-182), and the returned group
     * reports them — a caller that reads the result instead of re-querying must not see the defaults.
     */
    @Test
    fun `createGroup should persist the given settings and return them on the created group`() = runTest {
        ensureChat(100L)

        val created = repository
            .createGroup(
                100L,
                "devs",
                GroupSettingsPatch(icon = Patch.Value("🔥"), pingMark = Patch.Value(PingMark.Custom("🦀"))),
            ).getOrThrow()

        assertEquals("🔥", created.icon)
        assertEquals(PingMark.Custom("🦀"), created.pingMark)

        val reread = repository.findGroupByKey(100L, "devs").getOrThrow()
        assertEquals("🔥", reread.icon)
        assertEquals(PingMark.Custom("🦀"), reread.pingMark)
    }

    @Test
    fun `createGroup without settings should leave the defaults in place`() = runTest {
        ensureChat(100L)

        val created = repository.createGroup(100L, "devs").getOrThrow()

        assertNull(created.icon)
        assertEquals(PingMark.Default, created.pingMark)
        assertEquals(PingMark.Default, repository.findGroupByKey(100L, "devs").getOrThrow().pingMark)
    }

    @Test
    fun `createGroup should return failure when duplicate name in same chat`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val result = repository.createGroup(100L, "devs")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DuplicateResourceException)
    }

    @Test
    fun `createGroup should allow same name in different chats`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        val result1 = repository.createGroup(100L, "devs")
        val result2 = repository.createGroup(200L, "devs")

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun `findGroupByKey should return group when exists`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val result = repository.findGroupByKey(100L, "devs")

        assertTrue(result.isSuccess)
        val group = result.getOrThrow()
        assertEquals("devs", group.name)
        assertEquals(100L, group.chatId)
    }

    @Test
    fun `findGroupByKey should return failure when not exists`() = runTest {
        val result = repository.findGroupByKey(100L, "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `findGroupByKey should not find group from different chat`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val result = repository.findGroupByKey(200L, "devs")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `findGroupById should return group with its settings when exists`() = runTest {
        ensureChat(100L)
        val created = repository.createGroup(100L, "devs").getOrThrow()
        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))
        repository.setReadinessEnabled(100L, "devs", false)

        val result = repository.findGroupById(100L, created.id)

        assertTrue(result.isSuccess)
        val group = result.getOrThrow()
        assertEquals("devs", group.name)
        assertEquals(100L, group.chatId)
        assertEquals("🔥", group.icon)
        assertEquals(false, group.readinessEnabled)
    }

    @Test
    fun `findGroupById should return failure when not exists`() = runTest {
        val result = repository.findGroupById(100L, 9999L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `findGroupById should not find group from different chat`() = runTest {
        ensureChat(100L)
        val created = repository.createGroup(100L, "devs").getOrThrow()

        val result = repository.findGroupById(200L, created.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `getAllGroups should return empty list when no groups`() = runTest {
        val result = repository.getAllGroups(100L)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAllGroups should return only groups for given chatId`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        repository.createGroup(100L, "devs")
        repository.createGroup(100L, "ops")
        repository.createGroup(200L, "other")

        val result = repository.getAllGroups(100L)

        assertTrue(result.isSuccess)
        val groups = result.getOrThrow()
        assertEquals(2, groups.size)
        assertEquals(setOf("devs", "ops"), groups.map { it.name }.toSet())
    }

    @Test
    fun `deleteGroup should remove existing group`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val deleteResult = repository.deleteGroup(100L, "devs")
        assertTrue(deleteResult.isSuccess)

        val findResult = repository.findGroupByKey(100L, "devs")
        assertTrue(findResult.isFailure)
    }

    @Test
    fun `deleteGroup should return failure when group not exists`() = runTest {
        val result = repository.deleteGroup(100L, "nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `deleteGroup should not delete group from different chat`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val deleteResult = repository.deleteGroup(200L, "devs")
        assertTrue(deleteResult.isFailure)

        val findResult = repository.findGroupByKey(100L, "devs")
        assertTrue(findResult.isSuccess)
    }

    @Test
    fun `a new group should have readiness enabled`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        assertTrue(repository.findGroupByKey(100L, "devs").getOrThrow().readinessEnabled)
    }

    @Test
    fun `setReadinessEnabled should persist the flag`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.setReadinessEnabled(100L, "devs", false)

        assertTrue(update.isSuccess)
        assertEquals(false, repository.findGroupByKey(100L, "devs").getOrThrow().readinessEnabled)
    }

    @Test
    fun `setReadinessEnabled should fail for an unknown group`() = runTest {
        ensureChat(100L)

        val result = repository.setReadinessEnabled(100L, "nonexistent", false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `setReadinessEnabled should not touch a same-named group in another chat`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        repository.createGroup(100L, "devs")
        repository.createGroup(200L, "devs")

        repository.setReadinessEnabled(100L, "devs", false)

        assertTrue(repository.findGroupByKey(200L, "devs").getOrThrow().readinessEnabled)
    }

    @Test
    fun `a new group should have no icon`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        assertNull(repository.findGroupByKey(100L, "devs").getOrThrow().icon)
    }

    @Test
    fun `updateGroup should persist the icon`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))

        assertTrue(update.isSuccess)
        assertEquals("🔥", repository.findGroupByKey(100L, "devs").getOrThrow().icon)
    }

    @Test
    fun `updateGroup should surface the icon through getAllGroups`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))

        assertEquals(
            "🔥",
            repository
                .getAllGroups(100L)
                .getOrThrow()
                .single()
                .icon,
        )
    }

    @Test
    fun `updateGroup with a null icon should clear it`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))

        val update = repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value(null)))

        assertTrue(update.isSuccess)
        assertNull(repository.findGroupByKey(100L, "devs").getOrThrow().icon)
    }

    @Test
    fun `updateGroup should fail for an unknown group`() = runTest {
        ensureChat(100L)

        val result = repository.updateGroup(100L, "nonexistent", GroupSettingsPatch(icon = Patch.Value("🔥")))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `updateGroup should not touch a same-named group in another chat`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        repository.createGroup(100L, "devs")
        repository.createGroup(200L, "devs")

        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))

        assertNull(repository.findGroupByKey(200L, "devs").getOrThrow().icon)
    }

    @Test
    fun `updateGroup should leave untouched fields alone`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))

        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀"))))

        val group = repository.findGroupByKey(100L, "devs").getOrThrow()
        assertEquals("🔥", group.icon)
        assertEquals(PingMark.Custom("🦀"), group.pingMark)
    }

    @Test
    fun `a new group should use the default ping mark`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        assertEquals(PingMark.Default, repository.findGroupByKey(100L, "devs").getOrThrow().pingMark)
    }

    @Test
    fun `updateGroup should persist a custom ping mark`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀"))))

        assertTrue(update.isSuccess)
        assertEquals(PingMark.Custom("🦀"), repository.findGroupByKey(100L, "devs").getOrThrow().pingMark)
    }

    /**
     * The point of storing the flag apart from the emoji: hiding a mark must not forget it. Turning
     * it back on has to bring the same emoji back, not the locale default.
     */
    @Test
    fun `hiding a ping mark should preserve the custom emoji behind it`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀"))))

        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Hidden)))
        assertEquals(PingMark.Hidden, repository.findGroupByKey(100L, "devs").getOrThrow().pingMark)

        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀"))))
        assertEquals(PingMark.Custom("🦀"), repository.findGroupByKey(100L, "devs").getOrThrow().pingMark)
    }

    @Test
    fun `restoring the default ping mark should drop the custom emoji`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀"))))

        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Default)))

        assertEquals(PingMark.Default, repository.findGroupByKey(100L, "devs").getOrThrow().pingMark)
    }

    /** `off → default → off` must not resurrect an emoji the `default` step deliberately dropped. */
    @Test
    fun `a default in between should clear what a later hide has to fall back to`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀"))))

        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Hidden)))
        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Default)))
        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Hidden)))

        assertEquals(PingMark.Hidden, repository.findGroupByKey(100L, "devs").getOrThrow().pingMark)
        assertNull(transaction { GroupSettings.selectAll().single()[GroupSettings.pingMark] })
    }

    @Test
    fun `updateGroup should rename a group`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.updateGroup(100L, "devs", GroupSettingsPatch(name = Patch.Value("devops")))

        assertTrue(update.isSuccess)
        assertTrue(repository.findGroupByKey(100L, "devs").isFailure)
        assertEquals("devops", repository.findGroupByKey(100L, "devops").getOrThrow().name)
    }

    /** Order-independence: the settings write resolves the group before the rename moves the key. */
    @Test
    fun `updateGroup should apply a rename and a setting in the same call`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.updateGroup(
            100L,
            "devs",
            GroupSettingsPatch(name = Patch.Value("devops"), pingMark = Patch.Value(PingMark.Custom("🦀"))),
        )

        assertTrue(update.isSuccess)
        val group = repository.findGroupByKey(100L, "devops").getOrThrow()
        assertEquals(PingMark.Custom("🦀"), group.pingMark)
    }

    @Test
    fun `updateGroup should reject a rename onto an existing group`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.createGroup(100L, "ops")

        val update = repository.updateGroup(100L, "devs", GroupSettingsPatch(name = Patch.Value("ops")))

        assertTrue(update.isFailure)
        assertTrue(update.exceptionOrNull() is DuplicateResourceException)
        assertTrue(repository.findGroupByKey(100L, "devs").isSuccess)
    }

    @Test
    fun `updateGroup should accept a rename to the group's own name`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        val update = repository.updateGroup(100L, "devs", GroupSettingsPatch(name = Patch.Value("devs")))

        assertTrue(update.isSuccess)
        assertTrue(repository.findGroupByKey(100L, "devs").isSuccess)
    }

    @Test
    fun `updateGroup should allow the same name in another chat`() = runTest {
        ensureChat(100L)
        ensureChat(200L)
        repository.createGroup(100L, "devs")
        repository.createGroup(200L, "ops")

        val update = repository.updateGroup(200L, "ops", GroupSettingsPatch(name = Patch.Value("devs")))

        assertTrue(update.isSuccess)
        assertTrue(repository.findGroupByKey(100L, "devs").isSuccess)
        assertTrue(repository.findGroupByKey(200L, "devs").isSuccess)
    }

    /** Both settings share one row, so each write must name only the column it owns. */
    @Test
    fun `updateGroup and setReadinessEnabled should not clobber each other`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")

        repository.setReadinessEnabled(100L, "devs", false)
        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))

        val group = repository.findGroupByKey(100L, "devs").getOrThrow()
        assertEquals("🔥", group.icon)
        assertEquals(false, group.readinessEnabled)

        repository.setReadinessEnabled(100L, "devs", true)

        val updated = repository.findGroupByKey(100L, "devs").getOrThrow()
        assertEquals("🔥", updated.icon)
        assertTrue(updated.readinessEnabled)
    }

    @Test
    fun `a ping mark write should not clobber the icon or readiness`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))
        repository.setReadinessEnabled(100L, "devs", false)

        repository.updateGroup(100L, "devs", GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀"))))

        val group = repository.findGroupByKey(100L, "devs").getOrThrow()
        assertEquals("🔥", group.icon)
        assertEquals(false, group.readinessEnabled)
        assertEquals(PingMark.Custom("🦀"), group.pingMark)
    }

    /**
     * The repository derives `onUpdateExclude` from [GroupSettings.columns], so a new column is
     * protected from sibling writes automatically — but nothing derives its *test*. This one fails
     * the moment a column is added, as a prompt to give it non-clobber coverage like the tests above.
     */
    @Test
    fun `a new GroupSettings column needs its own non-clobber test`() {
        assertEquals(
            setOf("group_id", "icon", "readiness_enabled", "ping_mark", "ping_mark_enabled"),
            GroupSettings.columns.map { it.name }.toSet(),
        )
    }

    @Test
    fun `deleting a group should remove its settings row`() = runTest {
        ensureChat(100L)
        repository.createGroup(100L, "devs")
        repository.updateGroup(100L, "devs", GroupSettingsPatch(icon = Patch.Value("🔥")))

        repository.deleteGroup(100L, "devs")

        assertEquals(0, transaction { GroupSettings.selectAll().count() })
    }
}
