plugins {
    id("spovishun.kotlin-library")
    // ReleaseNoteDto is @Serializable — the data layer owns JSON (de)serialization that the
    // domain whitelist (ADR-0001) intentionally excludes.
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json) // ReleaseNoteDto
    implementation(libs.dotenv) // DatabaseConfig.fromEnv() for the migration dev-tool
    implementation(libs.slf4j.api) // DatabaseFactory / H2TestDatabaseFactory logging

    // Exposed (ORM)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.date)
    implementation(libs.exposed.migration)

    // Connection pool + DB migration + drivers
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.h2)
}

// Per-module detekt baseline (ADR-0001: each module carries its own accepted-debt baseline).
detekt {
    baseline = file("detekt-baseline.xml")
}

tasks.test {
    useJUnitPlatform()
}

// Migration dev-tool lives with the schema it diffs. Registered here so the task's working
// directory defaults to this module — MigrationGenerator's relative
// `src/main/resources/db/migration/postgresql` path then resolves inside :data.
tasks.register<JavaExec>("generateMigration") {
    group = "database"
    description = "Generate SQL migration statements"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ua.astrumon.data.tools.MigrationGeneratorKt")
    environment("PROFILE", "dev")
    standardInput = System.`in`
}
