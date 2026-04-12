import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.api.plugins.JavaPluginExtension

fun Project.registerAppTasks() {

    val sourceSets = the<JavaPluginExtension>().sourceSets
    val mainClasspath = sourceSets["main"].runtimeClasspath

    tasks.register("generateVersionInfo") {
        group = "build"
        description = "Generate version info file"
        doLast {
            val f = file("src/main/kotlin/common/util/VersionInfo.kt")
            f.writeText("""
                package com.ua.astrumon.common.util

                object VersionInfo {
                    private const val VERSION = "$version"
                    const val BOT_NAME = "Spovishun"
                    fun getFullVersion(): String = BOT_NAME + " v" + VERSION
                }
            """.trimIndent())
            println("Generated VersionInfo.kt with version: $version")
        }
    }

    tasks.register<JavaExec>("runDev") {
        group = "application"
        description = "Run the application in development mode"
        classpath = mainClasspath
        mainClass.set("com.ua.astrumon.MainKt")
        environment("PROFILE", "dev")
    }

    tasks.register<JavaExec>("runProd") {
        group = "application"
        description = "Run the application in production mode"
        classpath = mainClasspath
        mainClass.set("com.ua.astrumon.MainKt")
        environment("PROFILE", "prod")
    }

    tasks.register<JavaExec>("generateMigration") {
        group = "database"
        description = "Generate SQL migration statements"
        classpath = mainClasspath
        mainClass.set("com.ua.astrumon.tools.MigrationGeneratorKt")
        environment("PROFILE", "dev")
    }

    tasks.register<Exec>("syncSkillsToNotion") {
        group = "notion"
        description = "Sync .claude/skills/*/SKILL.md files to Notion"
        commandLine("python", ".claude/scripts/sync-skills-to-notion.py")
    }

    tasks.named("compileKotlin") {
        dependsOn("generateVersionInfo")
    }
}