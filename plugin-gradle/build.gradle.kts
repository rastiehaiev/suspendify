import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.rastiehaiev.getDeployConfiguration
import io.github.rastiehaiev.resolveProperty

plugins {
    id("buildlogic.kotlin-common-conventions")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")
    id("maven-publish")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
    compileOnly(libs.kotlin.gradle.plugin)
    testImplementation(libs.kotlin.gradle.plugin)
}

private val deployConfiguration = project.getDeployConfiguration()

gradlePlugin {
    website = project.resolveProperty("plugin.github.url")
    vcsUrl = project.resolveProperty("plugin.github.url.vcs")
    plugins {
        create("suspendifyPlugin") {
            id = "${deployConfiguration.groupId}.${deployConfiguration.gradleArtifactId}"
            displayName = deployConfiguration.gradleArtifactId
            implementationClass = "${deployConfiguration.groupId}.SuspendifyGradlePlugin"
            description = deployConfiguration.gradleArtifactDescription
            tags.set(listOf("compiler", "kotlin"))
        }
    }
}

publishing {
    publications {
        withType<MavenPublication>().configureEach {
            artifactId = deployConfiguration.gradleArtifactId
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}
