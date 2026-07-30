plugins {
    id("spovishun.kotlin-common")
    application
    // Only the e2e TelegramHelperBot declares @Serializable DTOs (it talks to the Telegram HTTP API
    // directly via ktor). The plugin is harmless for the other source sets, which have no @Serializable.
    id("org.jetbrains.kotlin.plugin.serialization")
}

// kotlin-telegram-bot is published on jitpack and leaks transitively through :bot's `api`
// dependency. The shared kotlin-common convention only declares mavenCentral(), so :app — as the
// consumer that resolves that transitive artifact — adds the jitpack repository itself (same as :bot).
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Modules — :app is the composition root and wires the full stack together.
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":bot"))
    implementation(project(":admin-api"))

    // Koin (DI)
    implementation(libs.koin.core)

    // Env (AppConfig reads .env via dotenv)
    implementation(libs.dotenv)

    // Coroutines (Main.runBlocking + scheduler scopes)
    implementation(libs.kotlinx.coroutines.core)

    // Logging backend + logback.xml live with the app entry point.
    implementation(libs.logback)

    // Force secure versions of transitive dependencies on the runtime/dist classpath
    // (okhttp/okio arrive transitively via :bot's telegram-bot api dependency).
    constraints {
        implementation(libs.okhttp)
        implementation(libs.okio)
    }

    // Tests
    testImplementation(libs.kotlin.test)
    // koin-test provides verify() — the static DI-graph check in KoinModuleGraphTest (spovishun-156).
    testImplementation(libs.koin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.h2)
    // The integration/e2e test infra (TestDatabaseFactory/TestDatabaseCleaner) drives the DB
    // stack directly. :data exposes these as `implementation` (not transitive to compile), so
    // the test source sets compile against them here; the rest of the stack (exposed-jdbc,
    // flyway-postgresql, postgresql driver) arrives transitively from :data on the runtime classpath.
    testImplementation(libs.exposed.core)
    testImplementation(libs.hikari)
    testImplementation(libs.flyway.core)
}

application {
    mainClass.set("com.ua.astrumon.MainKt")
    // Preserve the launcher/dist name so the distribution stays build/install/spovishun and the
    // Dockerfile ENTRYPOINT ["./bin/spovishun"] remains valid after the move to a subproject.
    applicationName = "spovishun"
}

// Per-module detekt baseline (ADR-0001: each module carries its own accepted-debt baseline).
detekt {
    baseline = file("detekt-baseline.xml")
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
    // IntegrationDbConfig reads the root `.env.e2e` via dotenv from the JVM working directory; pin it
    // to the repo root so local runs find it (the task otherwise defaults to this subproject's dir).
    workingDir = rootProject.projectDir
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

// ktor client + JSON serialization are used ONLY by the e2e TelegramHelperBot (real Telegram API).
// Scoped to the e2e source set rather than the shared implementation classpath.
dependencies {
    "e2eTestImplementation"(libs.ktor.client.core)
    "e2eTestImplementation"(libs.ktor.client.cio)
    "e2eTestImplementation"(libs.ktor.client.content.negotiation)
    "e2eTestImplementation"(libs.ktor.serialization.kotlinx.json)
    "e2eTestImplementation"(libs.kotlinx.serialization.json)
}

tasks.register<Test>("e2eTest") {
    description = "Runs end-to-end tests against real Telegram API"
    group = "verification"
    testClassesDirs = e2eTestSourceSet.output.classesDirs
    classpath = e2eTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = 1
    // Hard ordering, not a preference: both tasks point at the same database and TestDatabaseFactory
    // opens with flyway.clean(), which drops the schema. Under `shouldRunAfter` a combined invocation
    // (./gradlew integrationTest e2eTest) let the two overlap and one wiped the schema out from under
    // the other — "flyway_schema_history does not exist" (spovishun-160).
    mustRunAfter(tasks.named("integrationTest"))
    // E2EConfig/E2EDbConfig read the root `.env.e2e` via dotenv from the JVM working directory; pin it
    // to the repo root so local runs find it (the task otherwise defaults to this subproject's dir).
    workingDir = rootProject.projectDir
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
