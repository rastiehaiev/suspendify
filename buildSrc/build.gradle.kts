plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.gradle.plugin.publish)
    implementation(libs.gradleup.shadow.jar)
    implementation(libs.maven.central.publish)
}
