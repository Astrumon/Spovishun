// Root project is a pure aggregator after the multi-module migration (spovishun-100..105): all code
// and the composition root now live in the :common/:domain/:data/:bot/:app subprojects. The root only
// carries shared coordinates and lints its own build scripts.
//
// ktlint is applied with no version here because buildSrc puts the plugin on the build classpath for
// every project (spovishun-100, ADR-0001); the same rule applies to the convention plugins.
plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

group = "com.ua.astrumon"

// `version` is single-sourced here and consumed by :common's generateVersionInfo (rootProject.version).
version = "1.8.0"

// ktlint resolves its CLI from here when linting the root build scripts (subprojects get their
// repositories from the spovishun.kotlin-common convention).
repositories {
    mavenCentral()
}

// ktlint lints the root build scripts (build.gradle.kts / settings.gradle.kts); subproject sources are
// covered by the shared spovishun.kotlin-common convention. Tool version comes from the catalog.
ktlint {
    version.set(
        libs.versions.ktlint.tool
            .get(),
    )
}
