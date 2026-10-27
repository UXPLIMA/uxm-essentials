plugins {
    id("uxm.paper-plugin")
}

uxplima {
    slug = "uxmessentials"
}

// The optional REST add-on. It compiles against the published developer API only: :bukkit-api for the front door
// and the events, :api for the views, queries and actions. Everything is compileOnly: the host jar carries these
// classes and paper-plugin.yml joins its classpath, so a shaded copy would be a second class with the same name.
dependencies {
    compileOnly(project(":bukkit-api"))
    compileOnly(project(":api"))
    compileOnly(libs.gson)
    compileOnly(uxm.configurate.hocon)
}

tasks.shadowJar {
    archiveBaseName = "uxmEssentials-rest"
}
