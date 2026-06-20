plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    // Exposes the version catalog (`libs`) to precompiled convention plugins —
    // it is not on their compile classpath by default (known Gradle gotcha).
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
