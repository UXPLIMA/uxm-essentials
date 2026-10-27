plugins {
    id("uxm.paper-plugin")
}

uxplima {
    slug = "uxmessentials"
}

// The optional Discord bridge. It is its own Paper plugin that consumes the host plugin's ports and events through
// Bukkit's ServicesManager. JDA is not shaded: UxmDiscordLoader downloads it at boot.
dependencies {
    implementation(project(":core"))
    // The host jar carries :api and paper-plugin.yml joins its classpath. A shaded copy is a second class with
    // the same name, and a service the host registers under it is invisible to the bridge's lookup.
    compileOnly(project(":api"))
    compileOnly(libs.jda)
    compileOnly(uxm.configurate.hocon)
}

tasks.shadowJar {
    archiveBaseName = "uxmEssentials-discord"
    // :core brings :api along as its own api dependency. The host's copy is the one the bridge must use.
    dependencies { exclude(project(":api")) }
}
