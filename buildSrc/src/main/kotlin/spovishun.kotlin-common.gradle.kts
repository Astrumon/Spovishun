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

    // The pre-commit hook (.githooks/pre-commit) passes the staged Kotlin files via
    // -PinternalKtlintGitFilter as a whitespace-separated list of repo-root-relative paths. When set,
    // narrow each module's ktlint to just the staged files it owns, so the hook formats staged content
    // only — not the whole tree. Absent property = lint everything (normal builds / CI).
    val stagedFilter = providers.gradleProperty("internalKtlintGitFilter").orNull
    if (!stagedFilter.isNullOrBlank()) {
        val stagedFiles = stagedFilter
            .split(' ', '\n', '\r', '\t')
            .filter { it.isNotBlank() }
            .map { rootProject.projectDir.resolve(it.trim()).absoluteFile }
            .toSet()
        filter {
            // Return true for directories so the tree is still traversed; include only staged files.
            include { element -> element.isDirectory || element.file.absoluteFile in stagedFiles }
        }
    }
}

// detekt owns static analysis; shared config lives at the repo root. Per-module baselines are
// added per task as each module receives code.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}
