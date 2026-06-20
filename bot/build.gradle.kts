plugins {
    id("spovishun.kotlin-library")
}

// kotlin-telegram-bot is published on jitpack; the shared kotlin-common convention only
// declares mavenCentral(), so the bot module adds the jitpack repository itself.
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Layer rule (spovishun-104): :bot depends ONLY on :domain + :common.
    implementation(project(":domain"))
    implementation(project(":common"))

    // Telegram Bot type leaks through TelegramBot's public API (create/verifyIdentity/startPolling
    // expose `Bot`), so it is exposed transitively to the app module via `api`.
    api(libs.telegram.bot)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Per-module detekt baseline (ADR-0001: each module carries its own accepted-debt baseline).
detekt {
    baseline = file("detekt-baseline.xml")
}

tasks.test {
    useJUnitPlatform()
}
