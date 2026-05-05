plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.ua.astrumon"
version = "1.2.0"

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
