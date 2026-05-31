plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// The optional Discord bridge (docs/09-deployment.md Path C). It is its own Paper
// plugin jar (uxmessentials-discord) that consumes the host plugin's ports/events
// through Bukkit's ServicesManager — there is no compile-time link to :bukkit-adapter.
// JDA is the only backend, shaded and relocated; opus-java is excluded because the
// bridge only posts text and never touches voice.

dependencies {
    implementation(project(":core"))
    api(project(":api"))
    compileOnly(libs.paper.api)

    implementation(libs.jda) {
        // Voice/opus is dead weight for a text-only bridge — drop the native codec.
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation(libs.bundles.configs)

    testImplementation(libs.bundles.testing)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveBaseName.set("uxmessentials-discord")
    archiveClassifier.set("")
    // Single per-plugin namespace so two plugins shading JDA at different versions
    // do not clash on the classpath (docs/04-build.md §16).
    relocate("net.dv8tion.jda", "com.uxplima.uxmessentials.libs.jda")
    relocate("org.spongepowered.configurate", "com.uxplima.uxmessentials.libs.configurate")
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }
