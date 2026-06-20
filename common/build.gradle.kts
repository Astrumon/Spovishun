plugins {
    id("spovishun.kotlin-library")
}

dependencies {
    testImplementation(libs.kotlin.test)
}

// VersionInfo is owned by :common (the module that holds it on the classpath for every consumer,
// including :bot). Generated from the root project version so a release bump stays single-sourced.
// The output file is gitignored — `compileKotlin` regenerates it on every build.
val generateVersionInfo by tasks.registering {
    group = "build"
    description = "Generate version info file"
    val target = file("src/main/kotlin/common/util/VersionInfo.kt")
    val botVersion = rootProject.version.toString()
    inputs.property("version", botVersion)
    outputs.file(target)
    doLast {
        // Output must satisfy ktlint (blank line before the function, trailing newline).
        target.writeText(
            """
            package com.ua.astrumon.common.util

            object VersionInfo {
                const val VERSION = "$botVersion"
                const val BOT_NAME = "Spovishun"

                fun getFullVersion(): String = BOT_NAME + " v" + VERSION
            }
            """.trimIndent() + "\n",
        )
        println("Generated VersionInfo.kt with version: $botVersion")
    }
}

// Every task that reads the main source set consumes the generated VersionInfo.kt, so each must
// depend on its producer (Gradle 9 fails the build on an undeclared implicit dependency otherwise).
tasks
    .matching {
        it.name == "compileKotlin" || it.name == "detekt" || it.name.startsWith("runKtlint")
    }.configureEach {
        dependsOn(generateVersionInfo)
    }

// Per-module detekt baseline (ADR-0001: each module carries its own accepted-debt baseline as it
// receives code). Holds the intentional generic catch in ResultContainer.catching.
detekt {
    baseline = file("detekt-baseline.xml")
}

tasks.test {
    useJUnitPlatform()
}
