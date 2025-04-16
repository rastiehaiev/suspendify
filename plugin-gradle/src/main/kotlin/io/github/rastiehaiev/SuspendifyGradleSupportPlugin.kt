package io.github.rastiehaiev

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class SuspendifyGradleSupportPlugin : KotlinCompilerPluginSupportPlugin {

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> = with(kotlinCompilation.target) {
        project.provider {
            val extension = project.getExtension()
            if (extension.enabled) {
                listOf(SubpluginOption("enabled", "true"))
            } else {
                emptyList()
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = with(kotlinCompilation.target.project) {
        plugins.hasPlugin(SuspendifyGradleSupportPlugin::class.java) && getExtension().enabled
    }

    override fun getCompilerPluginId(): String = with(PluginConfiguration) { "$GROUP_ID.$ARTIFACT_ID_GRADLE" }

    override fun getPluginArtifact() = with(PluginConfiguration) {
        SubpluginArtifact(
            groupId = GROUP_ID,
            artifactId = ARTIFACT_ID_KOTLIN,
            version = VERSION,
        )
    }

    private fun Project.getExtension(): SuspendifyGradlePluginExtension =
        extensions.findByType(SuspendifyGradlePluginExtension::class.java) ?: SuspendifyGradlePluginExtension()
}
