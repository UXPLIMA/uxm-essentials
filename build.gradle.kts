// No root `plugins {}` block. Spotless / Error Prone / NullAway are pulled
// onto every subproject's buildscript classpath via the `buildSrc` convention
// plugin. Declaring them here again with `apply false` causes Gradle 9.x
// to fail with "plugin already on classpath with an unknown version".

allprojects {
    group = "com.uxplima"
    version = project.findProperty("projectVersion")?.toString() ?: "0.9.1"

    repositories {
        mavenLocal() // uxmLib is consumed from ~/.m2 during the dogfood (publishToMavenLocal)
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.org/repository/maven-public/")  // Treasury economy API
        maven("https://jitpack.io")                                 // Vault economy API
        maven("https://repo.extendedclip.com/releases/")           // PlaceholderAPI
        maven("https://repo.mikeprimm.com/")                        // Dynmap API (squaremap API is on Maven Central)
        maven("https://repo.opencollab.dev/main/")                  // Floodgate/Cumulus Bedrock-form API
    }
}

// The one place the version number lives, readable from a shell. The release workflow and the sample-consumer
// job both need it, and parsing it out of this file with sed is how that kind of thing goes wrong quietly.
tasks.register("printVersion") {
    val current = version.toString()
    doLast { println(current) }
}
