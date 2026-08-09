package infrastructure

import com.ua.astrumon.data.db.DataSourceFactory
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

/**
 * Owns the test database connection for the `integrationTest` and `e2eTest` source sets.
 *
 * Initialization is destructive — it opens with `flyway.clean()`, which drops the schema — so it
 * must happen **once per JVM**, not once per test class. The pool is therefore released by a JVM
 * shutdown hook rather than an `@AfterAll`: a class-scoped `shutdown()` would make the next class
 * re-enter [initialize] and wipe the schema again mid-run (spovishun-160).
 */
object TestDatabaseFactory {
    private var dataSource: HikariDataSource? = null
    private var initializedUrl: String? = null
    private var shutdownHookRegistered = false

    fun initialize(
        url: String,
        driver: String = "org.postgresql.Driver",
        username: String = "postgres",
        password: String = "",
        poolSize: Int = 2,
    ) {
        if (dataSource != null) {
            check(initializedUrl == url) {
                "TestDatabaseFactory already initialized with '$initializedUrl' — cannot reinitialize with '$url'. Call shutdown() first."
            }
            return
        }

        val hikariConfig = DataSourceFactory.create(
            url = url,
            driver = driver,
            username = username,
            password = password,
            poolSize = poolSize,
        )
        val ds = HikariDataSource(hikariConfig)
        Database.connect(ds)

        val flyway = Flyway
            .configure()
            .dataSource(ds)
            .locations("classpath:db/migration/postgresql")
            .cleanDisabled(false)
            .load()
        flyway.clean()
        flyway.migrate()

        initializedUrl = url
        dataSource = ds

        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(Thread(::shutdown, "test-db-shutdown"))
            shutdownHookRegistered = true
        }
    }

    fun shutdown() {
        dataSource?.close()
        dataSource = null
        initializedUrl = null
    }
}
