plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// The optional REST add-on. Its own Paper plugin jar (uxmEssentials-rest), dormant until an operator turns it on.
//
// It compiles against the published developer API and nothing else: :bukkit-api for the front door and the events,
// :api for the views, the queries and the actions. That restriction is the point. If a REST endpoint cannot be
// written without reaching into :core or :bukkit-adapter, the published API has a hole in it, and the hole gets
// filled there (with its guard and its documentation) rather than worked around here.
//
// Everything is compileOnly. The host jar carries these classes, and paper-plugin.yml joins its classpath, so
// shading a second copy would give two class objects with the same name and a LinkageError the first time one
// crossed the boundary. Gson and configurate-hocon arrive at boot through UxmRestLoader.

dependencies {
    compileOnly(project(":bukkit-api"))
    compileOnly(project(":api"))
    compileOnly(libs.paper.api)
    compileOnly(libs.gson)
    compileOnly(libs.bundles.configs)

    testImplementation(project(":bukkit-api"))
    testImplementation(project(":api"))
    // :core is on the test classpath only, for the guard that keeps UxmRestLoader's pinned coordinates in step
    // with the ones every other loader resolves. No production class here may import it.
    testImplementation(project(":core"))
    testImplementation(libs.paper.api)
    testImplementation(libs.gson)
    testImplementation(libs.bundles.configs)
    testImplementation(libs.archunit.junit)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveBaseName.set("uxmEssentials-rest")
    archiveClassifier.set("")
    // Nothing to bundle: every dependency is either the host's or the loader's. The shadow task is here so the
    // add-on produces the same one-jar artifact the other three do.
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }
