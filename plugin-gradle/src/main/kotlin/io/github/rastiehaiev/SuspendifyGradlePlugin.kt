package io.github.rastiehaiev

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class SuspendifyGradlePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.extensions.create("suspendify", SuspendifyGradlePluginExtension::class.java)
        target.plugins.apply(SuspendifyGradleSupportPlugin::class.java)
    }
}

open class SuspendifyGradlePluginExtension(var enabled: Boolean = false)
