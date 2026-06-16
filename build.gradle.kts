plugins {
    // Versions are omitted: buildSrc puts these plugins on the build classpath for every
    // project, so the root can no longer request them with an explicit version. The same
    // plugins are still applied — behavior is unchanged. (spovishun-100, ADR-0001)
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
    application
}

group = "com.ua.astrumon"
version = "1.5.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Tests
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.h2)

    // Env
    implementation(libs.dotenv)

    // Koin (DI)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines + Flow
    implementation(libs.kotlinx.coroutines.core)

    // Exposed (ORM)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.date)
    implementation(libs.exposed.migration)

    // Database drivers
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)

    // Connection pool
    implementation(libs.hikari)

    // Db Migration
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Logging
    implementation(libs.logback)

    // Ktor (HTTP server)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Retrofit (HTTP client)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    // Telegram
    implementation(libs.telegram.bot)

    // Force secure versions of transitive dependencies
    constraints {
        implementation(libs.okhttp)
        implementation(libs.okio)
    }
}

kotlin {
    jvmToolchain(21)
}

// ktlint owns code formatting (indentation, imports, syntax). Rules come from .editorconfig.
ktlint {
    version.set("1.5.0")
    // Build scripts (*.kts) are checked together with source sets.
}

// detekt owns static analysis (code smells, complexity, structure) — NOT formatting,
// which is delegated to ktlint to avoid running the same rules twice (detekt-formatting omitted).
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(
        files(
            "src/main/kotlin",
            "src/test/kotlin",
            "src/integrationTest/kotlin",
            "src/e2eTest/kotlin",
        ),
    )
}

application {
    mainClass.set("com.ua.astrumon.MainKt")
}

registerAppTasks()

tasks.test {
    useJUnitPlatform()
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    kotlin.srcDir("src/integrationTest/kotlin")
    resources.srcDir("src/integrationTest/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    System.getenv("E2E_DATABASE_URL")?.let { environment("E2E_DATABASE_URL", it) }
    System.getenv("E2E_DATABASE_USERNAME")?.let { environment("E2E_DATABASE_USERNAME", it) }
    System.getenv("E2E_DATABASE_PASSWORD")?.let { environment("E2E_DATABASE_PASSWORD", it) }
}

val e2eTestSourceSet = sourceSets.create("e2eTest") {
    kotlin.srcDir("src/e2eTest/kotlin")
    resources.srcDir("src/e2eTest/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations["e2eTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["e2eTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("e2eTest") {
    description = "Runs end-to-end tests against real Telegram API"
    group = "verification"
    testClassesDirs = e2eTestSourceSet.output.classesDirs
    classpath = e2eTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = 1
    shouldRunAfter(tasks.named("integrationTest"))
    // Env vars are read by E2EConfig via dotenv (falls back to .env file).
    // Only forward when explicitly set to avoid overriding dotenv with empty strings.
    System.getenv("TEST_BOT_TOKEN")?.let { environment("TEST_BOT_TOKEN", it) }
    System.getenv("TEST_HELPER_BOT_TOKEN")?.let { environment("TEST_HELPER_BOT_TOKEN", it) }
    System.getenv("TEST_CHAT_ID")?.let { environment("TEST_CHAT_ID", it) }
    System.getenv("TEST_ADMINS")?.let { environment("TEST_ADMINS", it) }
    System.getenv("E2E_DATABASE_URL")?.let { environment("E2E_DATABASE_URL", it) }
    System.getenv("E2E_DATABASE_USERNAME")?.let { environment("E2E_DATABASE_USERNAME", it) }
    System.getenv("E2E_DATABASE_PASSWORD")?.let { environment("E2E_DATABASE_PASSWORD", it) }
}
