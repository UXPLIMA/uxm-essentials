pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// uxmLib reaches this plugin the way it reaches the other twenty six: as the published artifact named in
// gradle/libs.versions.toml, resolved from repo.uxplima.com, and from mavenLocal() first so a locally
// published library build can be tried out. There is deliberately no composite build here.
//
// There was one, and it is worth saying what it cost. It substituted the library's sibling checkout for the
// pinned version whenever that checkout existed, so this build compiled against whatever the working tree
// held and never once against the number it pins. The pin read 0.93.0 while the only artifact published
// under the coordinates this file used was 0.46.0: a fresh clone without the sibling could not build at all,
// and nobody would have found out here. That is the same failure scripts/publish-lib.sh exists to stop, one
// level up.
//
// So a library change now goes the way it goes for every other plugin: release a new version of uxm-lib,
// which publishes it to repo.uxplima.com, and raise the pin here. To try one out before releasing it, run
// publishToMavenLocal in uxm-lib and raise the pin to that snapshot; mavenLocal() is ahead of the
// organisation repository so the local copy wins.

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
    ":rest-adapter",
)
