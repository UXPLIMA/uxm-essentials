pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "uxmEssentials"

include(
    ":api",
    ":core",
    ":bukkit-adapter",
    ":persistence-adapter",
    ":velocity-adapter",
    ":discord-adapter",
)
