package com.ua.astrumon.data.db

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.data.db.DatabaseFactory.logger
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun initialize(config: AppConfig) {
        try {
            logger.info("Initializing database")

            val hikariConfig = DataSourceFactory.create(
                url = config.databaseUrl,
                driver = config.databaseDriver,
                username = config.databaseUsername,
                password = config.databasePassword,
                poolSize = config.databasePoolSize
            )

            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource)

            logger.info("Database connection established. Running Flyway migrations...")

            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/postgresql")
                .baselineOnMigrate(true)
                .baselineVersion("0")
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
        } catch (e: BaseException) {
            throw e
        } catch (e: Exception) {
            throw DatabaseException("Database query failed", e)
        }
    }

suspend fun <T> safeDbQuery(block: () -> T): ResultContainer<T> =
    ResultContainer.catching {
        logger.debug("safeDbQuery: starting execution")
        val result = dbQuery { block() }
        logger.debug("safeDbQuery: execution completed successfully")
        result
    }.onFailure { exception ->
        logger.error("safeDbQuery: execution failed", exception)
    }

suspend fun <T> safeDbTransaction(block: () -> T): ResultContainer<T> =
    ResultContainer.catching { transaction { block() } }