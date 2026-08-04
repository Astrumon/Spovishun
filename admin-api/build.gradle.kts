plugins {
    id("spovishun.kotlin-library")
    // DTOs in dto/ are @Serializable — they form the JSON contract the future spovishun-admin
    // client duplicates (spovishun-110). The data layer owns its own serialization; this module
    // owns the admin-API wire contract.
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    // :data is in the allowed set for this module (spovishun-110). DB access is funneled through the
    // domain ServerHealthRepository port, so no Exposed/JDBC type is ever imported here.
    implementation(project(":data"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.dotenv) // AdminApiConfig.fromEnv()
    implementation(libs.slf4j.api)

    // Ktor server (embedded observability API)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor client (read-only Docker API via docker-socket-proxy)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}
