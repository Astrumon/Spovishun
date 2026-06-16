import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

// Version catalog access inside a precompiled convention plugin (wired via buildSrc deps).
val libs = the<LibrariesForLibs>()

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

// ktlint owns formatting; tool version comes from the catalog (rules live in root .editorconfig).
ktlint {
    version.set(libs.versions.ktlint.tool.get())
}

// detekt owns static analysis; shared config lives at the repo root. Per-module baselines are
// added per task as each module receives code.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}
