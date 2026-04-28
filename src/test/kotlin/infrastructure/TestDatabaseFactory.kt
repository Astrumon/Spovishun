package infrastructure

import com.ua.astrumon.data.db.DataSourceFactory
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object TestDatabaseFactory {
    private var dataSource: HikariDataSource? = null
    private var initializedUrl: String? = null

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

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration/postgresql")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()

        initializedUrl = url
        dataSource = ds
    }

    fun shutdown() {
        dataSource?.close()
        dataSource = null
        initializedUrl = null
    }
}
