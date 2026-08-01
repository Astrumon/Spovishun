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

    // Bot.editMessageText returns Pair<retrofit2.Response<...>, Exception?>, so the type has to be
    // resolvable to call it at all — but retrofit is only a runtime dependency of telegram-bot.
    // compileOnly makes the signature visible without adding anything new to the runtime classpath.
    compileOnly(libs.retrofit)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    // Same reason as the compileOnly above — tests verify the editMessageText calls.
    testCompileOnly(libs.retrofit)
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
