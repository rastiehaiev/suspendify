import java.util.*

val properties = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}

private fun resolveProperty(name: String): String = properties.getProperty(name)?.toString()
    ?: error("Please define property '$name'.")

val pluginGroupId = resolveProperty("plugin.group.id")
val pluginArtifactId = resolveProperty("plugin.gradle.artifact.id")
val pluginLibsArtifactId = resolveProperty("plugin.libs.artifact.id")
val pluginVersion = resolveProperty("plugin.version")

gradle.extra.set("pluginId", "$pluginGroupId.$pluginArtifactId")
gradle.extra.set("pluginVersion", pluginVersion)

gradle.extra.set("pluginGroupId", pluginGroupId)
gradle.extra.set("pluginLibsArtifactId", pluginLibsArtifactId)

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == gradle.extra.get("pluginId")) {
                println("Resolved plugin: ${gradle.extra.get("pluginId")}:${gradle.extra.get("pluginVersion")}.")
                useVersion(gradle.extra.get("pluginVersion") as String)
            }
        }
    }
}

rootProject.name = "sample"
