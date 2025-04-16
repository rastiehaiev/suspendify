import io.github.rastiehaiev.getDeployConfiguration

plugins {
    id("buildlogic.kotlin-common-conventions")
    id("maven-publish")
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
}

with (project.getDeployConfiguration()) {
    ext["plugin.artifact.id"] = kotlinArtifactId
    ext["plugin.artifact.description"] = kotlinArtifactDescription
}

apply(plugin = "buildlogic.kotlin-maven-artifact-conventions")

kotlin {
    sourceSets.all {
        languageSettings.optIn("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}
