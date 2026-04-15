package infrastructure

import com.ua.astrumon.data.db.DataSourceFactory
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object TestDatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun initialize(config: E2EDbConfig) {
        if (dataSource != null) return

        val hikariConfig = DataSourceFactory.create(
            url = config.databaseUrl!!,
            driver = config.databaseDriver,
            username = config.databaseUsername,
            password = config.databasePassword,
            poolSize = config.databasePoolSize
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

        dataSource = ds
    }

    fun shutdown() {
        dataSource?.close()
        dataSource = null
    }
}
