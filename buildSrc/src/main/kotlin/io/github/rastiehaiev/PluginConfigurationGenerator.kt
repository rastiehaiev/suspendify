package io.github.rastiehaiev

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

abstract class PluginConfigurationGenerator @Inject constructor(
    @get:Input val pluginGroupId: String,
    @get:Input val pluginVersion: String,
    @get:Input val pluginGradleArtifactId: String,
    @get:Input val pluginKotlinArtifactId: String,
    @get:OutputDirectory val sourcesDir: File,
) : DefaultTask() {
    private val log = project.logger

    private val groupIdVarName = "GROUP_ID"
    private val gradleArtifactIdVarName = "ARTIFACT_ID_GRADLE"
    private val kotlinArtifactIdVarName = "ARTIFACT_ID_KOTLIN"
    private val versionVarName = "VERSION"

    private val pluginConfigFileName = "PluginConfiguration"

    @TaskAction
    fun generate() {
        File(sourcesDir, "$pluginConfigFileName.kt").writeText(
            """
            package io.github.rastiehaiev
            
            object $pluginConfigFileName {
                const val $groupIdVarName = "$pluginGroupId"
                const val $gradleArtifactIdVarName = "$pluginGradleArtifactId"
                const val $kotlinArtifactIdVarName = "$pluginKotlinArtifactId"
                const val $versionVarName = "$pluginVersion"
            }
            """.trimIndent()
        )
        log.lifecycle("Generated $pluginConfigFileName.kt.")
    }
}
