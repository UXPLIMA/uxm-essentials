plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // The version catalog accessor type used inside the precompiled script.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // Plugins the convention plugin applies via id("...") need to be on
    // buildSrc's compile classpath. Versions here must match libs.versions.toml.
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.2")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.4.0")
    implementation("net.ltgt.gradle:gradle-nullaway-plugin:3.0.0")
}
