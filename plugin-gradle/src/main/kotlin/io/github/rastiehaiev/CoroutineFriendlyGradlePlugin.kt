package io.github.rastiehaiev

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class CoroutineFriendlyGradlePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.extensions.create("coroutineFriendly", CoroutineFriendlyGradlePluginExtension::class.java)
        target.plugins.apply(CoroutineFriendlyGradleSupportPlugin::class.java)
    }
}

open class CoroutineFriendlyGradlePluginExtension(var enabled: Boolean = false)
