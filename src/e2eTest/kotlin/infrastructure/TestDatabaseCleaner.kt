package infrastructure

import com.ua.astrumon.data.db.dbQuery
import com.ua.astrumon.data.db.table.Chats
import com.ua.astrumon.data.db.table.GroupMembers
import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.data.db.table.MemberChats
import com.ua.astrumon.data.db.table.Members
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll

/**
 * Deletes all test data scoped to a specific [chatId] after each e2e test.
 *
 * Deletion order respects FK constraints:
 *   GroupMembers → Groups → MemberChats → Members → Chats
 *
 * Safety: refuses to run against a URL that looks like production.
 */
class TestDatabaseCleaner(private val databaseUrl: String) {

    private val prodUrlPatterns = listOf("neon.tech", "neon-", "prod")

    init {
        val lower = databaseUrl.lowercase()
        for (pattern in prodUrlPatterns) {
            check(pattern !in lower) {
                "SAFETY: refusing to run test cleanup against URL containing '$pattern'. URL: $databaseUrl"
            }
        }
    }

    suspend fun cleanupByChatId(chatId: Long) {
        dbQuery {
            // 1. Delete GroupMembers that belong to groups in this chat
            val groupIds = Groups.selectAll()
                .where { Groups.chatId eq chatId }
                .map { it[Groups.id] }
            if (groupIds.isNotEmpty()) {
                GroupMembers.deleteWhere { group inList groupIds }
            }

            // 2. Delete Groups in this chat
            Groups.deleteWhere { Groups.chatId eq chatId }

            // 3. Collect member IDs before removing their chat membership
            val memberIds = MemberChats.selectAll()
                .where { MemberChats.chatId eq chatId }
                .map { it[MemberChats.memberId] }

            // 4. Delete MemberChats for this chat
            MemberChats.deleteWhere { MemberChats.chatId eq chatId }

            // 5. Delete Members that have no remaining chat memberships after step 4
            if (memberIds.isNotEmpty()) {
                val membersWithOtherChats = MemberChats.selectAll()
                    .where { MemberChats.memberId inList memberIds }
                    .map { it[MemberChats.memberId] }
                    .toSet()
                val membersToDelete = memberIds.filter { it !in membersWithOtherChats }
                if (membersToDelete.isNotEmpty()) {
                    Members.deleteWhere { Members.id inList membersToDelete }
                }
            }

            // 6. Delete the Chat itself
            Chats.deleteWhere { Chats.chatId eq chatId }
        }
    }
}
