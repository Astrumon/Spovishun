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

    // `generateVersionInfo` is registered in :common (spovishun-104) — the module that owns
    // VersionInfo. It was retargeted there when the bot/presentation layer moved to :bot, which
    // now consumes VersionInfo transitively from :common rather than a root-generated copy.

    // AppConfig reads the gitignored root `.env` via dotenv from the JVM working directory. Since
    // :app is a subproject, pin the working dir to the repo root so local runs find `.env` (the task
    // would otherwise default to app/). Production passes env vars directly, so this only aids dev.
    val repoRoot = rootProject.projectDir

    tasks.register<JavaExec>("runDev") {
        group = "application"
        description = "Run the application in development mode"
        classpath = mainClasspath
        mainClass.set("com.ua.astrumon.MainKt")
        workingDir = repoRoot
        environment("PROFILE", "dev")
    }

    tasks.register<JavaExec>("runProd") {
        group = "application"
        description = "Run the application in production mode"
        classpath = mainClasspath
        mainClass.set("com.ua.astrumon.MainKt")
        workingDir = repoRoot
        environment("PROFILE", "prod")
    }

    // `generateMigration` is registered in :data (spovishun-103) — the module that owns the
    // schema, MigrationGenerator, and the migration resources.

    tasks.register<Exec>("syncSkillsToNotion") {
        group = "notion"
        description = "Sync .claude/skills/*/SKILL.md files to Notion"
        commandLine("python", ".claude/scripts/sync-skills-to-notion.py")
    }
}