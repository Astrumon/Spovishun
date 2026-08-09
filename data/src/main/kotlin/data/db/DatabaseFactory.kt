package com.ua.astrumon.data.db

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.DatabaseFactory.logger
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

object DatabaseFactory {
    val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    // Held so the pool can be released on shutdown. AtomicReference (not a plain var) because the
    // JVM shutdown hook calls [close] from a different thread than the one that ran [initialize].
    private val dataSourceRef = AtomicReference<HikariDataSource?>(null)

    fun initialize(config: DatabaseConfig) {
        try {
            logger.info("Initializing database")

            val hikariConfig = DataSourceFactory.create(
                url = config.url,
                driver = config.driver,
                username = config.username,
                password = config.password,
                poolSize = config.poolSize,
            )

            val dataSource = HikariDataSource(hikariConfig)
            // Published before migrations run so a Flyway failure still leaves the pool closeable;
            // any pool from a previous initialize() is released rather than orphaned.
            dataSourceRef.getAndSet(dataSource)?.close()
            Database.connect(dataSource)

            logger.info("Database connection established. Running Flyway migrations...")

            val flyway = Flyway
                .configure()
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

    /**
     * Closes the connection pool. Idempotent — a second call is a no-op (spovishun-155).
     *
     * Called last during shutdown, after the scheduler scopes are cancelled, so no coroutine is still
     * running a query when the pool goes away.
     */
    fun close() {
        dataSourceRef.getAndSet(null)?.let {
            logger.info("Closing database connection pool")
            it.close()
        }
    }
}

/**
 * The single entry point for every database call: moves the blocking JDBC work onto
 * [Dispatchers.IO], opens the transaction, and converts a throw into [ResultContainer.Failure].
 *
 * There is deliberately no bare `dbQuery` sibling and no `safeDbTransaction` (spovishun-173) — the
 * latter ran `transaction {}` on the caller's dispatcher, i.e. on the CPU-sized
 * [kotlinx.coroutines.Dispatchers.Default] pool, where a single query parks a large share of a
 * 1–2 core production VM.
 */
suspend fun <T> safeDbQuery(block: () -> T): ResultContainer<T> = withContext(Dispatchers.IO) {
    // Emitted from inside the hop on purpose. It is the only `:data` line a successful query
    // produces, and `ChatLogContextIntegrationTest` asserts it still carries the chat MDC — which is
    // what proves `MDCContext` survives the switch to `Dispatchers.IO` (spovishun-168).
    logger.debug("safeDbQuery: executing on {}", Thread.currentThread().name)
    ResultContainer
        .catching { transaction { block() } }
        .onFailure { exception -> logger.error("safeDbQuery: execution failed", exception) }
}
