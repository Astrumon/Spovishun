package data.db

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.GroupSettings
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.data.bot.table.MemberChats
import com.ua.astrumon.data.bot.table.Members
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object H2TestDatabaseFactory {
    private val logger = LoggerFactory.getLogger(H2TestDatabaseFactory::class.java)
    private var initialized = false

    fun initialize() {
        if (initialized) return
        try {
            logger.info("Initializing H2 test database...")

            val hikariConfig = HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
                driverClassName = "org.h2.Driver"
                username = "sa"
                password = ""
                maximumPoolSize = 3
                minimumIdle = 1
            }

            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource)

            transaction {
                SchemaUtils.create(Chats, Members, MemberChats, Groups, GroupMembers, GroupSettings)
            }

            initialized = true
            logger.info("H2 test database initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize H2 test database", e)
            throw DatabaseException("H2 test database initialization failed", e)
        }
    }
}

suspend fun <T> h2DbQuery(block: () -> T): T = withContext(Dispatchers.IO) {
    try {
        transaction { block() }
    } catch (e: Exception) {
        throw DatabaseException("H2 test database query failed", e)
    }
}
