import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
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

// detekt owns static analysis; shared config and baseline layout live here for every module.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    // ADR-0001: each module carries its own accepted-debt baseline. Since spovishun-169 this path is
    // a template, not a file — the plugin derives `detekt-baseline-<sourceSet>.xml` from it for every
    // source set the module owns (see the type-resolution note below). Identical in every module, so
    // it lives here rather than being repeated six times.
    baseline = file("detekt-baseline.xml")
}

// spovishun-169: 93 detekt rules implement `RequiresAnalysisApi` and only run when the analysed
// sources come with a compile classpath. Among them are exactly the rules that encode this project's
// own Kotlin rules — LongParameterList (`max 3 parameters`), InjectDispatcher (no hardcoded
// `Dispatchers.*`), UnsafeCallOnNullableType (no `!!`), SleepInsteadOfDelay (no `Thread.sleep` in
// tests). The whole-project `detekt` task has no classpath, so it skipped all of them silently.
//
// Keep `./gradlew detekt` as the entry point, but turn it into a lifecycle task over the
// `detekt<SourceSet>` tasks the plugin registers with type resolution. Two consequences follow:
//   * the `detekt<SourceSet>SourceSet` variants are the classpath-less duplicates — excluded here;
//   * baselines are now per source set (`detekt-baseline-<sourceSet>.xml`), derived from the
//     `baseline` path set above, instead of one `detekt-baseline.xml` per module.
//
// `detektBaseline` is aggregated the same way, so the documented `:<module>:detektBaseline` keeps
// regenerating every baseline the module owns rather than a classpath-less subset.
val typeResolutionDetektTasks =
    tasks.withType<Detekt>().matching { it.name != "detekt" && !it.name.endsWith("SourceSet") }

val typeResolutionBaselineTasks =
    tasks.withType<DetektCreateBaselineTask>()
        .matching { it.name != "detektBaseline" && !it.name.endsWith("SourceSet") }

tasks.named<Detekt>("detekt") {
    enabled = false
    dependsOn(typeResolutionDetektTasks)
}

tasks.named<DetektCreateBaselineTask>("detektBaseline") {
    enabled = false
    dependsOn(typeResolutionBaselineTasks)
}
