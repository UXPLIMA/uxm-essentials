plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// P0 stub: the JDA bridge for audit / economy notifications lands in a later
// phase. The module compiles and produces a relocated shaded jar so the build
// graph is complete; the JDA gateway wiring arrives with the discord work.

dependencies {
    implementation(project(":core"))
    api(project(":api"))
    compileOnly(libs.paper.api)
    implementation(libs.jda)
}

tasks.shadowJar {
    archiveBaseName.set("uxmessentials-discord")
    relocate("net.dv8tion.jda", "com.uxplima.uxmessentials.libs.jda")
    mergeServiceFiles()
}
