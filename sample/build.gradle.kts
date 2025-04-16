plugins {
    kotlin("jvm") version "2.1.20"
    id("io.github.rastiehaiev.ir-dump") version "0.0.8"
    id("io.github.rastiehaiev.coroutine-friendly")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
}

val pluginVersion = gradle.extra["pluginVersion"] as String
val pluginGroupId = gradle.extra["pluginGroupId"] as String
val pluginLibsArtifactId = gradle.extra["pluginLibsArtifactId"] as String

dependencies {
    implementation("$pluginGroupId:$pluginLibsArtifactId:$pluginVersion")
    implementation("io.github.rastiehaiev:ir-dump-annotations:0.0.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

irDump {
    enabled = true
}

coroutineFriendly {
    enabled = true
}
