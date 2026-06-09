// No root `plugins {}` block. Spotless / Error Prone / NullAway are pulled
// onto every subproject's buildscript classpath via the `buildSrc` convention
// plugin. Declaring them here again with `apply false` causes Gradle 9.x
// to fail with "plugin already on classpath with an unknown version".

allprojects {
    group = "com.uxplima"
    version = project.findProperty("projectVersion")?.toString() ?: "0.1.0"

    repositories {
        mavenLocal() // uxmLib is consumed from ~/.m2 during the dogfood (publishToMavenLocal)
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.org/repository/maven-public/")  // Treasury economy API
        maven("https://jitpack.io")                                 // Vault economy API
        maven("https://repo.extendedclip.com/releases/")           // PlaceholderAPI
    }
}
