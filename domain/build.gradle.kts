plugins {
    id("spovishun.kotlin-library")
}

dependencies {
    // :common is exposed transitively — domain public APIs return ResultContainer/exceptions from it.
    api(project(":common"))

    // Whitelist (ADR-0001): only stdlib, coroutines, datetime, slf4j. No serialization, Exposed, Koin.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime) // Instant appears in public repository signatures
    implementation(libs.slf4j.api) // internal logging in services

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
