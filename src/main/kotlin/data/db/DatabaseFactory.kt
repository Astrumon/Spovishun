package com.ua.astrumon.data.db

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.config.AppConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    
    fun initialize(config: AppConfig) {
        try {
            logger.info("Initializing database connection...")
            
            val hikariConfig = DataSourceFactory.create(
                url = config.databaseUrl,
                driver = config.databaseDriver,
                username = config.databaseUsername,
                password = config.databasePassword
            )
            
            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource)
            
            logger.info("Database connection established. Creating schema...")

            val migrationLocation = if (config.databaseUrl.contains("postgresql")) {
                "classpath:db/migration"
            } else {
                logger.info("PostgreSQL database is not supported yet")
                return
            }

            logger.info("Running Flyway migrations from: $migrationLocation")


            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocation)
                .baselineOnMigrate(true)
                .load()

            val result = flyway.migrate()
            logger.info("Flyway: applied ${result.migrationsExecuted} migration(s)")
        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw DatabaseException("Database initialization failed", e)
        }
    }
}

suspend fun <T> dbQuery(block: () -> T): T =
    withContext(Dispatchers.IO) {
        try {
            transaction { block() }
        } catch (e: Exception) {
            throw DatabaseException("Database query failed", e)
        }
    }

suspend fun <T> safeDbQuery(block: () -> T): com.ua.astrumon.common.result.ResultContainer<T> =
    com.ua.astrumon.common.result.ResultContainer.catching { dbQuery { block() } }

suspend fun <T> safeDbTransaction(block: () -> T): com.ua.astrumon.common.result.ResultContainer<T> =
    com.ua.astrumon.common.result.ResultContainer.catching { transaction { block() } }