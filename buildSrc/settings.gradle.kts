// buildSrc is a separate included build with its own settings — the root `libs` version catalog
// is not visible here by default. Re-declare it from the shared TOML so build-logic and
// convention plugins can reference `libs` (known Gradle gotcha).
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
