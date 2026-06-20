package com.ua.astrumon.data.tools

import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.data.bot.table.Members
import com.ua.astrumon.data.db.DataSourceFactory
import com.ua.astrumon.data.db.DatabaseConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val config = DatabaseConfig.fromEnv()

    val dataSource = HikariDataSource(
        DataSourceFactory.create(
            url = config.url,
            driver = config.driver,
            username = config.username,
            password = config.password,
            poolSize = 2,
        ),
    )

    Database.connect(dataSource)

    transaction {
        val migrationDir = File("src/main/resources/db/migration/postgresql")
        val nextVersion = migrationDir
            .listFiles { f -> f.name.matches(Regex("V\\d+__.*\\.sql")) }
            ?.mapNotNull {
                it.name
                    .removePrefix("V")
                    .substringBefore("__")
                    .toIntOrNull()
            }?.maxOrNull()
            ?.plus(1) ?: 1

        print("Enter migration description (e.g. add_member_lastname): ")
        val description = readlnOrNull() ?: "migration"

        MigrationUtils.generateMigrationScript(
            Groups,
            Members,
            GroupMembers,
            scriptDirectory = "src/main/resources/db/migration/postgresql",
            scriptName = "V${nextVersion}__$description",
        )

        println("WARN ✅ Created: V${nextVersion}__$description.sql")
    }

    dataSource.close()
    println("WARN ✅ Migration script generated!")
}
