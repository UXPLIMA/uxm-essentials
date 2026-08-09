pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// Local development: when uxmLib is checked out as a sibling directory, build against it directly as a
// composite build so library changes are picked up without a publish step. Without the sibling checkout the
// published com.uxplima.uxmlib artifacts resolve from mavenLocal normally.
val uxmLibDir = file("../uxmLib")

if (uxmLibDir.isDirectory) {
    includeBuild(uxmLibDir)
}

rootProject.name = "uxmEssentials"

include(
    ":api",
    ":bukkit-api",
    ":core",
    ":bukkit-adapter",
    ":persistence-adapter",
    ":migration",
    ":velocity-adapter",
    ":discord-adapter",
    ":redis-adapter",
)
