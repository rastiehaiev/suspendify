import io.github.rastiehaiev.getDeployConfiguration

plugins {
    id("buildlogic.kotlin-common-conventions")
    id("maven-publish")
}

with(project.getDeployConfiguration()) {
    ext["plugin.artifact.id"] = libsArtifactId
    ext["plugin.artifact.description"] = libsArtifactDescription
}

apply(plugin = "buildlogic.kotlin-maven-artifact-conventions")
