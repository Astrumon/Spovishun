package com.ua.astrumon.tools

import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.data.db.DataSourceFactory
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.data.db.table.Members
import com.ua.astrumon.data.db.table.GroupMembers
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import java.io.File

@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val config = AppConfig()

    val dataSource = HikariDataSource(DataSourceFactory.create(
        url = config.databaseUrl,
        driver = config.databaseDriver,
        username = config.databaseUsername,
        password = config.databasePassword,
        poolSize = 2
    ))

    Database.connect(dataSource)

    transaction {
        val migrationDir = File("src/main/resources/db/migration/postgresql")
        val nextVersion = migrationDir
            .listFiles { f -> f.name.matches(Regex("V\\d+__.*\\.sql")) }
            ?.mapNotNull { it.name.removePrefix("V").substringBefore("__").toIntOrNull() }
            ?.maxOrNull()
            ?.plus(1) ?: 1

        print("Enter migration description (e.g. add_member_lastname): ")
        val description = readlnOrNull() ?: "migration"

        MigrationUtils.generateMigrationScript(
            Groups, Members, GroupMembers,
            scriptDirectory = "src/main/resources/db/migration/postgresql",
            scriptName = "V${nextVersion}__${description}"
        )

        println("WARN ✅ Created: V${nextVersion}__${description}.sql")
    }

    dataSource.close()
    println("WARN ✅ Migration script generated!")
}