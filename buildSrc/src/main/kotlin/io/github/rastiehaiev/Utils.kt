package io.github.rastiehaiev

import org.gradle.api.Project

fun Project.resolveProperty(name: String): String = findProperty(name)?.toString()
    ?: error("Please define property '$name'.")

fun Project.getDeployConfiguration(): DeployConfiguration = DeployConfiguration(
    groupId = resolveProperty("plugin.group.id"),
    gradleArtifactId = resolveProperty("plugin.gradle.artifact.id"),
    gradleArtifactDescription = resolveProperty("plugin.gradle.artifact.description"),
    kotlinArtifactId = resolveProperty("plugin.kotlin.artifact.id"),
    kotlinArtifactDescription = resolveProperty("plugin.kotlin.artifact.description"),
    libsArtifactId = resolveProperty("plugin.libs.artifact.id"),
    libsArtifactDescription = resolveProperty("plugin.libs.artifact.description"),
    version = resolveProperty("plugin.version"),
)

fun Project.getDeployMetadata(): DeployMetadata = DeployMetadata(
    githubUrl = resolveProperty("plugin.github.url"),
    githubUrlVcs = resolveProperty("plugin.github.url.vcs"),
    developerId = resolveProperty("plugin.developer.id"),
    developerName = resolveProperty("plugin.developer.name"),
    developerEmail = resolveProperty("plugin.developer.email"),
    scmUrl = resolveProperty("plugin.scm.url"),
    scmConnection = resolveProperty("plugin.scm.connection"),
    scmDeveloperConnection = resolveProperty("plugin.scm.developer.connection"),
    issueManagementSystem = resolveProperty("plugin.scm.url"),
    issueManagementUrl = resolveProperty("plugin.issue.management.system"),
)

data class DeployConfiguration(
    val groupId: String,
    val gradleArtifactId: String,
    val gradleArtifactDescription: String,
    val kotlinArtifactId: String,
    val kotlinArtifactDescription: String,
    val libsArtifactId: String,
    val libsArtifactDescription: String,
    val version: String,
)

data class DeployMetadata(
    val githubUrl: String,
    val githubUrlVcs: String,
    val developerId: String,
    val developerName: String,
    val developerEmail: String,
    val scmUrl: String,
    val scmConnection: String,
    val scmDeveloperConnection: String,
    val issueManagementSystem: String,
    val issueManagementUrl: String,
)
