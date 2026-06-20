plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "spovishun"

include(":common", ":domain", ":data", ":bot", ":admin-api", ":app")
