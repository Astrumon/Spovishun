package infrastructure

import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.data.bot.table.MemberChats
import com.ua.astrumon.data.bot.table.Members
import com.ua.astrumon.data.db.safeDbQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll

/**
 * Deletes all test data scoped to a specific [chatId] after each test.
 *
 * Deletion order respects FK constraints:
 *   GroupMembers → Groups → MemberChats → Members → Chats
 *
 * ## Safety
 * The guard is an allowlist on the host, not just a blocklist on the name. Test databases always
 * live on the machine running the tests — a local PostgreSQL for developers, the `postgres` service
 * published on localhost in CI — while the production database is only ever reachable at a remote
 * host. A blocklist alone would have to guess every name production might use; requiring a loopback
 * host makes "which database is this?" a question about where it is, which cannot be misspelled
 * into a pass (spovishun-160). The name check stays as a second line of defence for a production
 * instance that someone tunnels to localhost.
 */
class TestDatabaseCleaner(
    private val databaseUrl: String,
) {
    init {
        val lower = databaseUrl.lowercase()
        val authority = lower.substringAfter("://", "").substringBefore("/")
        val host = authority.substringBeforeLast(":")
        val databaseName = lower.substringAfterLast("/", "")
        // The message names the host and database but never the URL: a JDBC URL may carry
        // credentials in its authority, and these failures surface in CI logs and in the JUnit XML
        // that e2e.yml uploads as an artifact (spovishun-160).
        check(host in LOCAL_HOSTS) {
            "SAFETY: refusing to run test cleanup against non-local host '$host'. " +
                "Test databases must be reachable on ${LOCAL_HOSTS.joinToString("/")}."
        }
        check(FORBIDDEN_NAME_PATTERNS.none { it in databaseName }) {
            "SAFETY: refusing to run test cleanup against database '$databaseName' — the name marks it as production."
        }
    }

    suspend fun cleanupByChatId(chatId: Long) {
        safeDbQuery {
            val groupIds = Groups
                .selectAll()
                .where { Groups.chatId eq chatId }
                .map { it[Groups.id] }
            if (groupIds.isNotEmpty()) {
                GroupMembers.deleteWhere { group inList groupIds }
            }

            Groups.deleteWhere { Groups.chatId eq chatId }

            val memberIds = MemberChats
                .selectAll()
                .where { MemberChats.chatId eq chatId }
                .map { it[MemberChats.memberId] }

            MemberChats.deleteWhere { MemberChats.chatId eq chatId }

            if (memberIds.isNotEmpty()) {
                val membersWithOtherChats = MemberChats
                    .selectAll()
                    .where { MemberChats.memberId inList memberIds }
                    .map { it[MemberChats.memberId] }
                    .toSet()
                val membersToDelete = memberIds.filter { it !in membersWithOtherChats }
                if (membersToDelete.isNotEmpty()) {
                    Members.deleteWhere { Members.id inList membersToDelete }
                }
            }

            Chats.deleteWhere { Chats.chatId eq chatId }
        }.getOrThrow()
    }

    private companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")
        val FORBIDDEN_NAME_PATTERNS = listOf("prod")
    }
}
