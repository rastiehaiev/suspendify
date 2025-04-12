import io.github.rastiehaiev.PluginConfigurationGenerator
import io.github.rastiehaiev.getDeployConfiguration
import io.github.rastiehaiev.resolveProperty

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("signing")
}

private val deployConfiguration = project.getDeployConfiguration()

private val pluginGroupId = deployConfiguration.groupId
private val pluginVersionNumber = deployConfiguration.version

group = pluginGroupId
version = pluginVersionNumber

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.1")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val sourcesDir =
    File(project.layout.buildDirectory.asFile.get(), "generated-sources/src/main/kotlin/io/github/rastiehaiev")
val pluginConfigurationGeneratorTask = tasks.register<PluginConfigurationGenerator>(
    "generateBuildConfig",
    pluginGroupId,
    pluginVersionNumber,
    deployConfiguration.gradleArtifactId,
    deployConfiguration.kotlinArtifactId,
    sourcesDir,
)

tasks.named("compileKotlin") {
    dependsOn("generateBuildConfig")
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        main {
            kotlin.srcDir(pluginConfigurationGeneratorTask.map { it.sourcesDir })
        }
    }
}

tasks.register("publishPluginsAndLibs") {
    group = project.resolveProperty("plugin.tasks.group.name")

    val publishTasks = listOf(
        "publishToMavenLocal",
        "publishToCentralPortal",
        "publishPlugins",
    ).mapNotNull { project.tasks.findByName(it) }

    if (publishTasks.isNotEmpty()) {
        setDependsOn(publishTasks)
        println("[${project.name}] Publishing task(s): ${publishTasks.map { it.name }}")
    } else {
        println("No publish tasks found in project '${project.name}'.")
    }
}
